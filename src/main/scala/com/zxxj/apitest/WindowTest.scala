package com.zxxj.apitest

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.{AssignerWithPeriodicWatermarks, AssignerWithPunctuatedWatermarks}
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.streaming.api.watermark.Watermark
import org.apache.flink.streaming.api.windowing.time.Time

/**
 * @author shkstart
 * @create 2020-05-07 20:37
 */

// 定义传感器数据样例类
case class SensorReading(id: String, timestamp: Long, temperature: Double)


object WindowTest {
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
    // 开窗
    val result: DataStream[(String, Double)] = dataStream.map(data => {
      (data.id, data.temperature)
    }).keyBy(_._1)
      .timeWindow(Time.seconds(10), Time.seconds(3))
      .reduce((result, data) => (data._1, result._2.min(data._2)))
    result.print()
    env.execute("window api test")

  }

  // 自定义一个周期性的时间戳抽取
  class MyAssigner() extends AssignerWithPeriodicWatermarks[SensorReading] {

    // 定义固定延迟为3秒
    val bound: Long = 3 * 1000L

    // 定义当前收到的最大的时间戳
    var maxTs: Long = Long.MinValue

    //定义当前水位线
    override def getCurrentWatermark: Watermark = {
      new Watermark(maxTs - bound)

    }

    // 抽取时间戳
    override def extractTimestamp(element: SensorReading, previousElementTimestamp: Long): Long = {
      maxTs = maxTs.max(element.timestamp * 1000L)
      element.timestamp
    }
  }

  // 自定义时间戳抽取，根据某些条件对每条数据进行筛选和处理
  class MyAssigner1() extends AssignerWithPunctuatedWatermarks[SensorReading] {
    val bound: Long = 1000L

    // 如何确定水位线，如果输入的数据的id是sensor_1，那么生产新的水位线
    override def checkAndGetNextWatermark(lastElement: SensorReading, extractedTimestamp: Long): Watermark = {
      if (lastElement.id == "sensor_1") {
        new Watermark(extractedTimestamp - bound)

      } else {
        null
      }
    }

    //抽取时间戳
    override def extractTimestamp(element: SensorReading, previousElementTimestamp: Long): Long = {
      element.timestamp
    }
  }

}
