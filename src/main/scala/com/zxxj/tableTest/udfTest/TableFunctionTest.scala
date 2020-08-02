package com.zxxj.tableTest.udfTest

import com.zxxj.apitest.SensorReading
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.scala._
import org.apache.flink.table.functions.TableFunction
import org.apache.flink.types.Row

/**
 * @author shkstart
 * @create 2020-08-02 9:50
 */
object TableFunctionTest {
  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 创建表执行环境
    val tableEnv = StreamTableEnvironment.create(env)

    val inputStream: DataStream[String] = env.readTextFile("D:\\IdeaProjects\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
    //    val inputStream: DataStream[String] = env.socketTextStream("localhost", 7777)

    // map成样例类类型
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })
      .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor[SensorReading](Time.seconds(1)) {
        override def extractTimestamp(element: SensorReading): Long = element.timestamp * 1000L
      })

    // 将流转换成表，直接定义时间字段
    val sensorTable: Table = tableEnv.fromDataStream(dataStream, 'id, 'temperature, 'timestamp.rowtime as 'ts)



    // 使用自定义Split函数，将ID切分开
    val split = new Split("_")

    //    // Table API 调用方式
    val resultTable: Table = sensorTable
      .joinLateral(split('id) as('word, 'length)) // 侧向连接，应用TableFunction
      .select('id, 'ts, 'word, 'length)


    // SQL调用方式，首先要注册表和函数
    tableEnv.createTemporaryView("sensor",sensorTable)
    tableEnv.registerFunction("split",split)
    val resultSqlTable: Table = tableEnv.sqlQuery(
      """
        |
        |select id , ts , word , length
        |from
        |sensor , lateral table (split(id))  as splitid(word , length)
        |""".stripMargin)


    // 将结果转成流输出
    resultTable.toAppendStream[Row].print("resultTable")
    resultSqlTable.toAppendStream[Row].print("sql")


    env.execute("Table Function")


  }



  // 自定义TableFunction，实现分割字符串并统计长度(word, length)
  class Split(separator: String) extends TableFunction[(String, Int)]{
    def eval(str:String): Unit ={
      str.split(separator).foreach(
        word => collect(word,word.length)
      )
    }



  }


}
