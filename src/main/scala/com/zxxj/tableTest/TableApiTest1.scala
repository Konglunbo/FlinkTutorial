package com.zxxj.tableTest

import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, EnvironmentSettings, Table, TableEnvironment}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors._

/**
 * @author shkstart
 * @create 2020-07-19 16:28
 */
object TableApiTest1 {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)

    // 1. 创建表执行环境
    val tableEnv = StreamTableEnvironment.create(env)

    // 2. 连接外部系统，读取数据
    // 2.1 读取文件数据
    val filePath = "C:\\Users\\kongfanxin.CITICCFC\\IdeaProjects\\FlinkTutorial\\src\\main\\resources\\sensor.txt"

    tableEnv.connect(new FileSystem().path(filePath))
      .withFormat(new OldCsv())
      .withSchema(new Schema()
        .field("id", DataTypes.STRING())
        .field("timestamp", DataTypes.BIGINT())
        .field("temperature", DataTypes.DOUBLE())
      ) //定义表结构
      .createTemporaryTable("inputTable") // 在表环境注册一张表


    // 2.2 消费kafka数据
    tableEnv.connect(new Kafka()
      .version("0.11")
      .topic("sensor")
      .property("zookeeper.connect", "60.60.40.63:2181")
      .property("bootstrap.servers", "60.60.40.63:9092")
    )
      .withFormat(new OldCsv())
      .withSchema(new Schema()
        .field("id", DataTypes.STRING())
        .field("timestamp", DataTypes.BIGINT())
        .field("temperature", DataTypes.DOUBLE())
      ) //定义表结构
      .createTemporaryTable("kafkaInputTable") // 在表环境注册一张表

    // 3. 表的查询转换
    val sensorTable: Table = tableEnv.from("kafkaInputTable")

    // 3.1 简单查询转换
    val resultTable: Table = sensorTable.select('id, 'temperature)
      .filter('id === "sensor_1")
    // 3.2 聚合转换
    val aggResultTable: Table = sensorTable
      .groupBy('id)
      .select('id, 'id.count as 'count)

    tableEnv.sqlQuery("select id, count(id) as cnt from kafkaInputTable group by id")

    // 测试输出
    resultTable.toAppendStream[(String, Double)].print("result")
    aggResultTable.toRetractStream[(String, Long)].print("agg result")
    env.execute("table api test job")

  }



}
