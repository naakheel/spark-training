package de.dimajix.training.spark.jdbc

import java.util.Properties

import scala.collection.JavaConversions._

import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
  * Created by kaya on 03.12.15.
  */
object AnalyzeDriver {
    def main(args: Array[String]) : Unit = {
        // First create driver, so can already process arguments
        val driver = new AnalyzeDriver(args)

        // Now create SparkContext (possibly flooding the console with logging information)
        val sql = SparkSession
            .builder()
            .appName("Spark JDBC Analyzer")
            .getOrCreate()

        // ... and run!
        driver.run(sql)
    }
}


class AnalyzeDriver(args: Array[String]) {
    private val logger: Logger = LoggerFactory.getLogger(classOf[AnalyzeDriver])

    @Option(name = "--dburi", usage = "JDBC connection", metaVar = "<connection>")
    private var dburi: String = "jdbc:mysql://localhost/training"
    @Option(name = "--dbuser", usage = "JDBC username", metaVar = "<db_user>")
    private var dbuser: String = "cloudera"
    @Option(name = "--dbpass", usage = "JDBC password", metaVar = "<db_password>")
    private var dbpassword: String = "cloudera"
    @Option(name = "--output", usage = "output dir", metaVar = "<outputDirectory>")
    private var outputPath: String = "weather/minmax"

    parseArgs(args)

    private def parseArgs(args: Array[String]) {
        val parser: CmdLineParser = new CmdLineParser(this)
        parser.setUsageWidth(80)
        try {
            parser.parseArgument(args.toList)
        }
        catch {
            case e: CmdLineException => {
                System.err.println(e.getMessage)
                parser.printUsage(System.err)
                System.err.println
                System.exit(1)
            }
        }
    }

    def run(sql: SparkSession) = {
        // Setup connection properties for JDBC
        val dbprops = new Properties
        dbprops.setProperty("user", dbuser)
        dbprops.setProperty("password", dbpassword)
        dbprops.setProperty("driver", "com.mysql.jdbc.Driver")

        // Load Weather data
        val weather = sql.read.jdbc(dburi, "weather", dbprops)

        // Load station data
        val ish = sql.read.jdbc(dburi, "isd", dbprops)

        weather.join(ish, weather("usaf") === ish("usaf") && weather("wban") === ish("wban"))
            .withColumn("year", weather("date").substr(0,4))
            .groupBy("country", "year")
            .agg(
                min(when(col("air_temperature_quality") === lit(1), col("air_temperature")).otherwise(9999)).as("temp_min"),
                max(when(col("air_temperature_quality") === lit(1), col("air_temperature")).otherwise(-9999)).as("temp_max"),
                min(when(col("wind_speed_quality") === lit(1), col("wind_speed")).otherwise(9999)).as("wind_min"),
                max(when(col("wind_speed_quality") === lit(1), col("wind_speed")).otherwise(-9999)).as("wind_max")
            )
            .coalesce(4)
            .write.parquet(outputPath)
    }
}
