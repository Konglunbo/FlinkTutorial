package com.zxxj.apitest

import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
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
}
