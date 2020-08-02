package com.zxxj.tableTest

import org.apache.flink.streaming.api.scala._
import org.apache.flink.table.api.{DataTypes, Table}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.descriptors.{Kafka, OldCsv, Schema}

/**
 * @author shkstart
 * @create 2020-07-25 16:47
 */
object KafkaPipelineTest {
  def main(args: Array[String]): Unit = {

    // /opt/cloudera/parcels/CDH-6.3.1-1.cdh6.3.1.p0.1470567/lib/kafka/bin/kafka-console-producer.sh --broker-list 60.60.40.63:9092,60.60.40.68:9092,60.60.40.69:9092 --topic sensor
    // /opt/cloudera/parcels/CDH-6.3.1-1.cdh6.3.1.p0.1470567/lib/kafka/bin/kafka-console-consumer.sh --bootstrap-server 60.60.40.63:9092,60.60.40.68:9092,60.60.40.69:9092  --topic sinkTest
    // 0. 创建流执行环境，读取数据并转换成样例类类型
    val env: StreamExecutionEnvironment = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    // 创建表环境
    val tableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env)


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

    // 输出流
    tableEnv.connect(new Kafka()
      .version("0.11")
      .topic("sinkTest")
      .property("zookeeper.connect", "60.60.40.63:2181")
      .property("bootstrap.servers", "60.60.40.63:9092")
    )
      .withFormat(new OldCsv())
      .withSchema(new Schema()
        .field("id", DataTypes.STRING())
        .field("temperature", DataTypes.DOUBLE())
      ) //定义表结构
      .createTemporaryTable("kafkaOutputTable") // 在表环境注册一张表
    resultTable.toAppendStream[(String, Double)].print("result")
    resultTable.insertInto("kafkaOutputTable")
    env.execute("kafka pipeline test job")


  }
}
