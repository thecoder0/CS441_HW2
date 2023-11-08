package com.natalia

import org.apache.spark.*
import org.apache.spark.graphx.{Graph, *}
import org.apache.spark.rdd.RDD
import org.apache.spark.util.IntParam
import org.apache.spark.{SparkConf, SparkContext}
import com.lsc.Main.logger
import com.google.common.graph.{EndpointPair, Graphs}
import NetGraphAlgebraDefs.NetModelAlgebra.{ALLTC, MAXPATHLENGTHTC, UNTILCYCLETC, graphWalkNodeTerminationProbability, graphWalkTerminationPolicy, maxWalkPathLengthCoeff}
import NetGraphAlgebraDefs.{Action, NetGraph, NodeObject}
import NetModelAnalyzer.{PATHRESULT, TerminationPolicy}

import scala.collection.mutable.Stack
import java.io.{File, PrintWriter}
import java.util.Optional
import java.util.stream.{Collector, Collectors}
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.RichOptional
import scala.util.Random

object Main {
  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("GraphMatching").setMaster("local[4]")
    val sc = new SparkContext(conf)

    loadGraphsAndAttack(sc)

    sc.stop()
  }

  def loadGraphsAndAttack(sc: SparkContext): Unit = {
    // 1.) Load original NetGraph

    val originalNetGraph = loadNetGraph(true)

    // 2.) Convert original NetGraph into GraphX object

    val originalGraphX = loadGraphX(originalNetGraph, sc, true)

    // 3.) Find all nodes in the original graph that have valuable data

    val originalValuableNodes: List[VertexId] = originalNetGraph.sm.nodes().stream()
      .filter(node => node.valuableData)
      .map(node => node.id.toLong)
      .collect(Collectors.toList[VertexId])
      .asScala.toList

    logger.info(s"Nodes with valuable data from ORIGINAL (total ${originalValuableNodes.size}): ${originalValuableNodes}")

    // 4.) Load perturbed NetGraph

    val perturbedNetGraph = loadNetGraph(false)

    // 5.) Convert perturbed NetGraph into GraphX object

    val perturbedGraphX = loadGraphX(perturbedNetGraph, sc, false)

    // 6.) Create a number of random walks on the perturbed graph in parallel and attack

    val maxNumStepsCoeff = Configuration.getMaxNumStepsCoefficient
    val maxNumSteps = scala.math.floor(maxNumStepsCoeff * perturbedGraphX.numVertices).toInt

    val randomWalkAttacker = RandomWalkAttacker(
      Configuration.getHowManyWalks,
      maxNumSteps,
      originalValuableNodes
    )
    val walkResult = randomWalkAttacker.walkAndAttack(
      perturbedGraphX,
      originalGraphX,
      sc
    )
    walkResult.foreach(pathThatWasWalked => {
      logger.info(s"Path that was walked: ${pathThatWasWalked}")
    })
  }

  def loadNetGraph(isOriginal: Boolean): NetGraph = {
    val resourcePath = if (isOriginal) Configuration.getOriginalGraphPath else Configuration.getPerturbedGraphPath
    val netGraphPath = Main.getClass.getResource(resourcePath).getPath
    val netGraphLoaded = NetGraph.load(netGraphPath, dir = "")
    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"

    if (netGraphLoaded.isEmpty) {
      val errorMessage = s"Loading ${originalOrPerturbedText} NetGraph FAILED - failing the job."
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    logger.info(s"Loading ${originalOrPerturbedText} NetGraph SUCCEEDED")

    val netGraph = netGraphLoaded.get
    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.totalNodes} nodes")
    logger.info(s"${originalOrPerturbedText} graph has ${netGraph.sm.edges().size()} edges")

    netGraph
  }

  def loadGraphX(netGraph: NetGraph, sc: SparkContext, isOriginal: Boolean): Graph[NodeObject, Double] = {
    // get GraphX vertices

    val verticesList = netGraph.sm.nodes().asScala.toList
      .map((node) => (node.id.toLong, node))

    val verticesRDD = sc.parallelize(verticesList)

    // Get perturbed GraphX edges
    val edgesList = netGraph.sm.edges().asScala.toList
      .map((edgeAsEndpointPair) => generateGraphXEdge(netGraph, edgeAsEndpointPair))

    val edgesRDD = sc.parallelize(edgesList)

    // Construct GraphX graph
    val graphX = Graph(verticesRDD, edgesRDD)

    graphX.subgraph()

    // Confirm that we've successfully constructed a GraphX graph
    val originalOrPerturbedText = if (isOriginal) "ORIGINAL" else "PERTURBED"
    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.vertices.count()} nodes")
    logger.info(s"the ${originalOrPerturbedText} GraphX has ${graphX.edges.count()} edges")

    graphX
  }

  def generateGraphXEdge(originalGraph: NetGraph, edgeAsEndpointPair: EndpointPair[NodeObject]): Edge[Double] = {
    val sourceNodeId = edgeAsEndpointPair.source().id
    val destinationNodeId = edgeAsEndpointPair.target().id
    val edgeValue = originalGraph.sm.edgeValue(edgeAsEndpointPair)

    if (edgeValue.toScala.isEmpty) {
      val errorMessage = s"Failed to read edge value from node ${sourceNodeId} to node ${destinationNodeId}"
      logger.error(errorMessage)
      throw new RuntimeException(errorMessage)
    }

    Edge(sourceNodeId, destinationNodeId, edgeValue.get().cost)
  }
}