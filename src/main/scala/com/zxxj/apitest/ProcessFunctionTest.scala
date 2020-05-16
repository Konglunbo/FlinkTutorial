package com.zxxj.apitest

import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.state.{ValueState, ValueStateDescriptor}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

/**
 * @author shkstart
 * @create 2020-05-08 21:13
 */
object ProcessFunctionTest {
  def main(args: Array[String]): Unit = {
    //获取环境
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    //设置并行度
    env.setParallelism(1)
    // 设置事件时间
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.getConfig.setAutoWatermarkInterval(500)
    // 读入socket流
    val inputStream: DataStream[String] = env.socketTextStream("dev-bigdata3", 7777)

    //对读入的数据切分，并封装到样例类中
    // sensor_1, 1547718199, 35.80018327300259
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray: Array[String] = data.split(",")
        SensorReading(dataArray(0).trim, dataArray(1).trim.toLong, dataArray(2).trim.toDouble)

      }).assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[SensorReading](Time.seconds(1)) {
      override def extractTimestamp(element: SensorReading): Long = element.timestamp * 1000L
    })

    // 定义温度报警 new TempIncreAlert()
    val processedStream: DataStream[String] = dataStream.keyBy(_.id)
      .process(new TempIncreAlert())



    // 第二种方式 定义温度报警 RichFlatMapFunction
    val processedStream2: DataStream[(String, Double, Double)] = dataStream.keyBy(_.id)
      .flatMap(new TempChangeAlert(10.0))

    // 第三种方式 定义温度报警 flatMapWithState
    val processedStream3: DataStream[(String, Double, Double)] = dataStream.keyBy(_.id)
      .flatMapWithState[(String, Double, Double), Double] {
        // 如果没有状态的话，也就是没有数据来过，那么就将当前数据温度值存入状态
        case (input: SensorReading, None) => (List.empty, Some(input.temperature))
        // 如果有状态，就应该与上次的温度值比较差值，如果大于阈值就输出报警
        case (input: SensorReading, lastTemp: Some[Double]) =>
          val diff: Double = (input.temperature - lastTemp.get).abs
          if (diff > 10.0) {
            (List((input.id, lastTemp.get, input.temperature)), Some(input.temperature))
          } else {
            (List.empty, Some(input.temperature))
          }
      }



    dataStream.print("input data")
    processedStream2.print("processed data")
    env.execute("process function test")
  }
}

class TempChangeAlert(threshold: Double) extends RichFlatMapFunction[SensorReading, (String, Double, Double)] {
  //声明状态变量
  private var lastTempState: ValueState[Double] = _
  // 重写OPEN方法，初始化的时候声明state变量


  override def open(parameters: Configuration): Unit = {
    lastTempState = getRuntimeContext.getState(new ValueStateDescriptor[Double]("lastTemp", classOf[Double]))
  }

  override def flatMap(value: SensorReading, out: Collector[(String, Double, Double)]): Unit = {
    //获取上次的温度值
    val lastTemp: Double = lastTempState.value()
    //用当前的温度值和上次的求差，如果大于阈值，输出报警信息
    val diff: Double = (value.temperature - lastTemp).abs
    if (diff > threshold) {
        out.collect((value.id,lastTemp,value.temperature))
    }
    lastTempState.update(value.temperature)

  }
}


class TempIncreAlert() extends KeyedProcessFunction[String, SensorReading, String] {

  // 定义一个状态，用来保存上一个数据的温度值
  lazy val lastTemp: ValueState[Double] = getRuntimeContext.getState(new ValueStateDescriptor[Double]("lastTemp", classOf[Double]))

  // 定义一个状态，用来保存定时器的时间戳
  lazy val currentTimer: ValueState[Long] = getRuntimeContext.getState(new ValueStateDescriptor[Long]("currentTimer", classOf[Long]))


  override def processElement(value: SensorReading, context: KeyedProcessFunction[String, SensorReading, String]#Context, out: Collector[String]): Unit = {
    // 先取出上一个温度值
    val preTemp = lastTemp.value()
    // 更新温度值
    lastTemp.update(value.temperature)
    // 取出定时器的时间戳
    val currentTimerTs = currentTimer.value()
    // 如果温度下降，或是第一条数据，删除定时器并清空状态
    if (value.temperature < preTemp || preTemp == 0.0) {
      context.timerService().deleteProcessingTimeTimer(currentTimerTs)
      currentTimer.clear()
      // 温度上升且没有设过定时器，则注册定时器
    } else if (value.temperature > preTemp || currentTimerTs == 0.0) {
      val timerTs: Long = context.timerService().currentProcessingTime() + 5000L
      context.timerService().registerProcessingTimeTimer(timerTs)
      currentTimer.update(timerTs)

    }


  }

  override def onTimer(timestamp: Long, ctx: KeyedProcessFunction[String, SensorReading, String]#OnTimerContext, out: Collector[String]): Unit = {
    out.collect(ctx.getCurrentKey + " 温度连续上升")
    currentTimer.clear()
  }
}




