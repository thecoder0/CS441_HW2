package com.natalia
import com.lsc.Main.logger
import com.typesafe.config.{Config, ConfigFactory}

object Configuration {
  val config: Config = ConfigFactory.load()
  logger.info("Configuration set up successfully")

  def getOriginalGraphPath: String = config.getString("originalGraphPath")
  def getPerturbedGraphPath: String = config.getString("perturbedGraphPath")
  def getMaxNumStepsCoefficient: Double = config.getDouble("maxNumStepsCoefficient")
  def getHowManyWalks: Int = config.getInt("howManyWalks")
  def getSimRankThreshold: Double = config.getDouble("simRankThreshold")
  def getSimRankMaxDepth: Int = config.getInt("simRankMaxDepth")
  def getSimRankDecayControl: Double = config.getDouble("simRankDecayControl")
}
