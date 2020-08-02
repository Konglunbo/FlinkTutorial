package com.zxxj.tableTest.udfTest

import com.zxxj.apitest.SensorReading
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.table.api.Table
import org.apache.flink.table.api.scala._
import org.apache.flink.table.functions.ScalarFunction
import org.apache.flink.types.Row

/**
 * @author shkstart
 * @create 2020-08-02 8:54
 */
object ScalarFunctionTest {
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


    // 使用自定义hash函数，求Id的哈希值
    val hashCode = new HashCode(1.68)

    //    // Table API 调用方式
    val resultTable: Table = sensorTable
      .select('id, 'ts, hashCode('id))

    // SQL调用方式，首先要注册表和函数
    tableEnv.createTemporaryView("sensor",sensorTable)
    tableEnv.registerFunction("hashCode",hashCode)

    val resultSqlTable: Table = tableEnv.sqlQuery(
      """
        |select id,ts,hashCode(id)
        |from sensor
        |""".stripMargin)



    //     // 转换成流打印输出
    resultTable.toAppendStream[Row].print("result")
    resultSqlTable.toAppendStream[Row].print("sql")

    env.execute("scalar Function")

  }

  //自定义求Hash Coded的标量函数
  class HashCode(factor: Double) extends ScalarFunction {
    def eval(value: String): Int = {
      (value.hashCode * factor.toInt)
    }


  }

}
