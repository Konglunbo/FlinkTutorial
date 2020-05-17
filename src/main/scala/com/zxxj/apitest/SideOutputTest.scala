package com.zxxj.apitest

import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.util.Collector

/**
 * @author shkstart
 * @create 2020-05-17 10:47
 */
object SideOutputTest {
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

    // 定义冰点报警 FreezingAlert()
    val processedStream: DataStream[String] = dataStream.keyBy(_.id)
      .process(new TempIncreAlert())
  }


  // 冰点报警，如果小于32F，输出报警信息到侧输出流
  class FreezingAlert() extends ProcessFunction[SensorReading,SensorReading] {
    override def processElement(value: SensorReading, ctx: ProcessFunction[SensorReading, SensorReading]#Context, out: Collector[SensorReading]): Unit = {
      if(value.temperature < 32){
        ctx.output( new OutputTag[String]("freezing alert"), "freezing alert" + value.id)
      }
      out.collect(value)
    }
  }

}
