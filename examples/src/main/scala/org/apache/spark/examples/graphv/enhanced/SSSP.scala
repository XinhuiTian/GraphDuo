
package org.apache.spark.examples.graphv.enhanced

import scala.collection.mutable

import org.apache.spark.graphv.enhanced._
import org.apache.spark.{SparkConf, SparkContext}

import org.apache.spark.graphv.VertexId

object SSSP {
  def main(args: Array[String]): Unit = {
    if (args.length < 2) {
      System.err.println (
        "Usage: GraphLoader <file> --numEPart=<num_edge_partitions> [other options]")
      System.exit (1)
    }

    val fname = args (0)
    val optionsList = args.drop (2).map{arg =>
      arg.dropWhile (_ == '-').split ('=') match {
        case Array (opt, v) => (opt -> v)
        case _ => throw new IllegalArgumentException ("Invalid argument: " + arg)
      }
    }

    val options = mutable.Map (optionsList: _*)

    val numEPart = options.remove ("numEPart").map (_.toInt).getOrElse{
      println ("Set the number of edge partitions using --numEPart.")
      sys.exit (1)
    }

    val iterations = options.remove("numIter").map(_.toInt).getOrElse {
      println ("Set the number of iterations using --numIter.")
      sys.exit (1)
    }

    val factor = options.remove("numFactor").map(_.toInt).getOrElse {
      println ("Set the number of iterations using --numIter.")
      sys.exit (1)
    }

    val conf = new SparkConf ()

    val sc = new SparkContext (conf.setAppName ("GraphLoad(" + fname + ")"))

    val myStartTime = System.currentTimeMillis
    val graph = GraphLoader.edgeListFile (sc, args (0), false, numEPart, factor, true).cache()

    graph.vertices.count ()
    println ("It took %d ms loadGraph".format (System.currentTimeMillis - myStartTime))

    // val landmark = graph.vertices.map(_._1).take(0)(0)

    // val landmark: Long = 61

    val spGraph = graph.mapVertices { (vid, _) => Double.PositiveInfinity }.cache()

    val initialMessage = Double.PositiveInfinity

    def initProgram(id: VertexId, attr: Double): Double = {
      if (id == 61L) 0.0 else Double.PositiveInfinity
    }

    def vertexProgram(id: VertexId, attr: Double, msg: Double): Double = math.min(attr, msg)

    /*
    def sendMessage(edge: GraphVEdgeTriplet[Double, _]): Iterator[(VertexId, Double)] = {
      if (edge.srcAttr < Double.PositiveInfinity) {
        Iterator((edge.dstId, edge.srcAttr + 1.0))
      } else {
        Iterator.empty
      }
    } */

    def sendMessage(edge: GraphVEdgeTriplet[Double, _]): Iterator[(VertexId, Double)] = {
      val newValue = edge.srcAttr + 1.0
      // println(edge.dstAttr)
      if (edge.srcAttr < Double.PositiveInfinity && newValue < edge.dstAttr) {
        Iterator ((edge.dstId, newValue))
      } else {
        Iterator.empty
      }
    }

    def mergeFunc(a: Double, b: Double): Double = math.min(a, b)

    val results = Pregel(spGraph, initialMessage,
      maxIterations = iterations,
      needActive = true, edgeFilter = true)(initProgram, vertexProgram, sendMessage, mergeFunc)

    // println (results.vertices.map (_._2).sum ())
    println ("My pregel " + (System.currentTimeMillis - myStartTime))
  }

}
