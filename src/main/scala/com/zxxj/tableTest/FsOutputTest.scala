package com.zxxj.tableTest

import com.zxxj.apitest.SensorReading
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors.{Csv, FileSystem, Schema}

/**
 * @author shkstart
 * @create 2020-07-25 16:17
 */
object FsOutputTest {
  def main(args: Array[String]): Unit = {
    // 0. 创建流执行环境，读取数据并转换成样例类类型
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    // 创建表环境
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env)

    //从文件读取数据
    val inputStream: DataStream[String] = env.readTextFile("D:\\IdeaProjects\\FlinkTutorial\\src\\main\\resources\\sensor.txt")

    // map成样例类类型
    val dataStream: DataStream[SensorReading] = inputStream
      .map(data => {
        val dataArray = data.split(",")
        SensorReading(dataArray(0), dataArray(1).toLong, dataArray(2).toDouble)
      })

    //把流转换成表
    val sensorTable: Table = tableEnv.fromDataStream(dataStream, 'id, 'temperature as 'temp, 'timestamp as 'ts)

    // 4. 进行表的转换操作
    // 4.1 简单查询转换
    val resultTable: Table = sensorTable.select('id, 'temp)
      .filter('id === "sensor_1")
    // 4.2 聚合转换
    val aggResultTable: Table = sensorTable
      .groupBy('id)
      .select('id, 'id.count as 'count)

    // 5. 将结果表输出到文件中
    tableEnv.connect(new FileSystem().path("D:\\IdeaProjects\\FlinkTutorial\\src\\main\\resources\\output.txt"))
      .withFormat(new Csv())
      .withSchema(new Schema()
        .field("id", DataTypes.STRING())
        .field("temp", DataTypes.DOUBLE())
      )
      .createTemporaryTable("outputTable")

    // 将转化后的数据插入到outPut
    resultTable.insertInto("outputTable")

    env.execute("fs output test job")

  }

}
