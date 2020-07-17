package com.zxxj.tableTest

import com.zxxj.apitest.SensorReading
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.scala.StreamTableEnvironment
import org.apache.flink.table.api.scala._

/**
 * @author shkstart
 * @create 2020-07-14 7:18
 */
object Example {
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

    // 1. 基于env创建表环境
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env)

    // 2. 基于tableEnv，将流转换成表
    val dataTable: Table = tableEnv.fromDataStream(dataStream)

    // 3. 转换操作，得到提取结果
    // 3.1 调用table api，做转换操作
    val resultTable: Table = dataTable.select("id,temperature")
      .filter("id =='sensor_1'")

    // 3.2 写sql实现转换
    //    tableEnv.registerTable("dataTable", dataTable)
    tableEnv.createTemporaryView("dataTable", dataTable)
    val resultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id, temperature
        |from dataTable
        |where id = 'sensor_1'
        |""".stripMargin)


    // 4. 把表转换成流，打印输出
    val resultStream: DataStream[(String, Double)] = resultTable
      .toAppendStream[(String, Double)]
    resultStream.print()

    env.execute("table api example job")
  }
}
