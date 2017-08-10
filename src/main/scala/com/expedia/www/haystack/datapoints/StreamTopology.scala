/*
 *
 *     Copyright 2017 Expedia, Inc.
 *
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 */

package com.expedia.www.haystack.datapoints

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import com.expedia.www.haystack.datapoints.config.entities.KafkaConfiguration
import com.expedia.www.haystack.datapoints.serde.DataPointSerde
import org.apache.kafka.common.serialization.Serdes.StringSerde
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KafkaStreams.StateListener
import org.apache.kafka.streams.kstream.KStreamBuilder
import org.apache.kafka.streams.processor.TopologyBuilder
import org.slf4j.LoggerFactory

class StreamTopology(kafkaConfig: KafkaConfiguration) extends StateListener
  with Thread.UncaughtExceptionHandler {

  private val LOGGER = LoggerFactory.getLogger(classOf[StreamTopology])
  private var streams: KafkaStreams = _
  private val running = new AtomicBoolean(false)
  private val TOPOLOGY_SOURCE_NAME = "datapoint-source"
  private val TOPOLOGY_SINK_NAME = "datapoint-aggegated-sink"
  private val TOPOLOGY_PROCESSOR_NAME = "datapoint-aggregator-process"

  Runtime.getRuntime.addShutdownHook(new ShutdownHookThread)

  /**
    * builds the topology and start kstreams
    */
  def start(): Unit = {
    LOGGER.info("Starting the kafka stream topology.")

    streams = new KafkaStreams(topology(), kafkaConfig.streamsConfig)
    streams.setStateListener(this)
    streams.setUncaughtExceptionHandler(this)
    streams.cleanUp()
    streams.start()
    running.set(true)
  }

  private def topology(): TopologyBuilder = {

    val builder = new KStreamBuilder()
    builder.stream(kafkaConfig.autoOffsetReset, kafkaConfig.timestampExtractor, new StringSerde(), DataPointSerde, kafkaConfig.consumeTopic).to(new StringSerde(), DataPointSerde, kafkaConfig.produceTopic)
    /*
    val builder = new TopologyBuilder()
    builder.addSource(
      kafkaConfig.autoOffsetReset,
      TOPOLOGY_SOURCE_NAME,
      kafkaConfig.timestampExtractor,
      new StringDeserializer,
      new DataPointDeserializer(),
      kafkaConfig.consumeTopic)

    builder.addSink(
      TOPOLOGY_SINK_NAME,
      kafkaConfig.produceTopic,
      new StringSerializer,
      new StringSerializer,
      TOPOLOGY_SOURCE_NAME)
      */
    builder

  }

  /**
    * on change event of kafka streams
    *
    * @param newState new state of kafka streams
    * @param oldState old state of kafka streams
    */
  override def onChange(newState: KafkaStreams.State, oldState: KafkaStreams.State): Unit = {
    LOGGER.info(s"State change event called with newState=$newState and oldState=$oldState")
  }

  /**
    * handle the uncaught exception by closing the current streams and rerunning it
    *
    * @param t thread which raises the exception
    * @param e throwable object
    */
  override def uncaughtException(t: Thread, e: Throwable): Unit = {
    LOGGER.error(s"uncaught exception occurred running kafka streams for thread=${t.getName}", e)
    // it may happen that uncaught exception gets called by multiple threads at the same time,
    // so we let one of them close the kafka streams and restart it
    if (closeKafkaStreams()) {
      start() // start all over again
    }
  }

  private def closeKafkaStreams(): Boolean = {
    if (running.getAndSet(false)) {
      LOGGER.info("Closing the kafka streams.")
      streams.close(30, TimeUnit.SECONDS)
      return true
    }
    false
  }

  private class ShutdownHookThread extends Thread {
    override def run(): Unit = closeKafkaStreams()
  }

}
