package com.zxxj.tableTest


import org.apache.flink.api.scala.ExecutionEnvironment
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.table.api.{EnvironmentSettings, TableEnvironment}
import org.apache.flink.table.api.scala._


/**
 * @author shkstart
 * @create 2020-07-16 6:58
 */
object TableApiTest {
  def main(args: Array[String]): Unit = {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    // 1. 创建表执行环境
//    val tableEnv = StreamTableEnvironment.create(env)

    // 1.1 老版本planner的流式查询
    val settings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useOldPlanner() //老版本
      .inStreamingMode() //流式处理
      .build()
    val oldStreamTableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env,settings)

    // 1.2 老版本Planner的批次查询
    val batchEnv: ExecutionEnvironment = ExecutionEnvironment.getExecutionEnvironment
    val batchTableEnv: BatchTableEnvironment = BatchTableEnvironment.create(batchEnv)

    // 1.3 blink版本的流式查询
    val bsSettings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useBlinkPlanner()
      .inStreamingMode()
      .build()
    val bsTableEnv: StreamTableEnvironment = StreamTableEnvironment.create(env,bsSettings)

    // 1.4 blink版本的批式查询
    val bbSettings: EnvironmentSettings = EnvironmentSettings.newInstance()
      .useBlinkPlanner()
      .inBatchMode()
      .build()

    val bbTableEnv = TableEnvironment.create(bbSettings)

    


  }
}
