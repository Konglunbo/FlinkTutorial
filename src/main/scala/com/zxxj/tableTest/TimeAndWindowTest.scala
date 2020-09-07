package com.zxxj.tableTest

import com.zxxj.apitest.SensorReading
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.{Over, Table, Tumble}
import org.apache.flink.table.api.scala._
import org.apache.flink.types.Row

/**
 * @author shkstart
 * @create 2020-07-29 6:51
 */
object TimeAndWindowTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

    // 创建表执行环境
    val tableEnv = StreamTableEnvironment.create(env)

    val inputStream: DataStream[String] = env.readTextFile("E:\\workspace\\workspace_scala\\FlinkTutorial\\src\\main\\resources\\sensor.txt")
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
//      val sensorTable: Table = tableEnv.fromDataStream(dataStream, 'id, 'temperature, 'timestamp, 'pt.proctime as 'ts)

    // 1. Table API
    // 1.1 Group Window聚合操作
    val resultTable: Table = sensorTable
      .window(Tumble over 10.seconds on 'ts as 'tw)
      .groupBy('id, 'tw)
      .select('id, 'id.count, 'tw.end)




    // 1.2 Over Window 聚合操作
    val overResultTable: Table = sensorTable
      .window(Over partitionBy 'id orderBy 'ts preceding 2.rows as 'ow)
      .select('id, 'ts, 'id.count over 'ow, 'temperature.avg over 'ow)

    // 2. SQL实现
    // 2.1 Group Windows
    tableEnv.createTemporaryView("sensor",sensorTable)
    val resultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id, count(id) , hop_end(ts , interval '4' second , interval '10' second)
        |from sensor
        |group by id , hop(ts , interval '4' second , interval '10' second)
        |""".stripMargin)

    val resultSqlTable1: Table = tableEnv.sqlQuery(
      """
        |select id, count(id) , TUMBLE_END(ts , INTERVAL '10' MINUTE),TUMBLE_START(ts , INTERVAL '10' MINUTE)
        |from sensor
        |group by id ,TUMBLE(ts, INTERVAL '10' MINUTE)
        |""".stripMargin)


    // 2.2 Over Window
    val orderSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id , ts , count(id) over w , avg(temperature) over w
        |from sensor
        |window w as (
        | partition by id
        | order by ts
        | rows between 2 preceding and current row 
        |)
        |
        |""".stripMargin)




    resultSqlTable1.toAppendStream[Row].print("orderSqlTable")
//    resultSqlTable.toAppendStream[Row].print("resultSqlTable")
//    overResultTable.toAppendStream[Row].print("over window")
//    resultTable.toRetractStream[Row].print("AGG")


    env.execute("time and window test job")


  }

}
