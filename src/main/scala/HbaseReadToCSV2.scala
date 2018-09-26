
import scala.reflect.runtime.universe

import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapred.JobConf
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import org.apache.spark.rdd.PairRDDFunctions
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.avg
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat
import org.apache.hadoop.fs.Path
import org.apache.spark._
import org.apache.hadoop._
import org.apache.hadoop.hbase.client.HBaseAdmin
import org.apache.hadoop.hbase.HTableDescriptor
import org.apache.spark.SparkContext
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import collection.immutable._


object HbaseReadToCSV2 {
  
  
  case class TableRow(rowkey: String, name: String, phone: String, city: String)
      
   object TableRow extends Serializable {
    def parseTableRow(result: Result): TableRow = {
      val rowkey = Bytes.toString(result.getRow())
      // remove time from rowKey, stats row key is for day
      val p0 = rowkey.split(" ")(0)
      val p1 = Bytes.toString(result.getValue(Bytes.toBytes("info"), Bytes.toBytes("name")))
      val p2 = Bytes.toString(result.getValue(Bytes.toBytes("info"), Bytes.toBytes("phone")))
      val p3 = Bytes.toString(result.getValue(Bytes.toBytes("info"), Bytes.toBytes("city")))
      
      TableRow(p0, p1, p2, p3)
    }
  }
  def dfSchema(columnNames: Seq[String]): StructType  =
    StructType(
      Seq(
        StructField(name = "rowkey",dataType = StringType, nullable = false),
        StructField(name = "name", dataType = StringType, nullable = true),
        StructField(name = "phone", dataType = StringType, nullable = true),
	StructField(name = "city", dataType = StringType, nullable = true)
    )
  )

  def main(args: Array[String]) {
     if (args.length != 2) {
        println("Need source hbase table and destination file name")
        System.exit(1)
    }
    val spark: SparkSession = SparkSession.builder.master("local").getOrCreate
    val sqlContext = spark.sqlContext//new org.apache.spark.sql.SQLContext(spark);  
    val conf = HBaseConfiguration.create()
    val tableName = args(0)
    val fileName = args(1)

    conf.setInt("timeout", 120000)
    conf.set(TableInputFormat.INPUT_TABLE, tableName)

    val admin = new HBaseAdmin(conf)
    if (!admin.isTableAvailable(tableName)) {
      val tableDesc = new HTableDescriptor(tableName)
      admin.createTable(tableDesc)
    }

    val hBaseRDD = sqlContext.sparkSession.sparkContext.newAPIHadoopRDD(conf, classOf[TableInputFormat], classOf[ImmutableBytesWritable], classOf[Result])
    println("Number of Records found : " + hBaseRDD.count())
    val resultRDD = hBaseRDD.map(tuple => tuple._2)
    val newhbaserdd = resultRDD.map(TableRow.parseTableRow)
    newhbaserdd.foreach(println)
    val dfWithSchema = spark.createDataFrame(newhbaserdd).toDF("rowkey","name", "phone", "city")
    dfWithSchema.show()
    dfWithSchema.write.csv(fileName)
    spark.stop()
  }
}


