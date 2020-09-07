package com.zxxj.apitest

import org.apache.flink.streaming.api.scala._

/**
 * @author shkstart
 * @create 2020-08-14 6:56
 */
//# case class SensorReading(id: String, timestamp: Long, temperature: Double)
object RedcueTest {
  def main(args: Array[String]): Unit = {
    // 0. 创建流执行环境，读取数据并转换成样例类类型
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment

    //从文件读取数据
    val inputStream: DataStream[String] = env.readTextFile("E:\\workspace\\workspace_scala\\FlinkTutorial\\src\\main\\resources\\sensor.txt")

    // map成样例类类型
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })


    //复杂聚合操作 reduce  得到当前id 最小的温度值以及最新的时间戳+1
    dataStream
      .keyBy("id")
      .reduce((curState, newData) => SensorReading(curState.id, newData.timestamp + 1, curState.temperature.min(newData.temperature)) )
      .print()


    env.execute("Reduce TEST")
  }

}
