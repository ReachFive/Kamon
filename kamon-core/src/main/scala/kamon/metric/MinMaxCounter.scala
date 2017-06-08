package kamon.metric

import java.lang.Math.abs
import java.util.concurrent.atomic.AtomicLong

import kamon.jsr166.LongMaxUpdater
import kamon.util.MeasurementUnit

import scala.concurrent.duration.Duration

trait MinMaxCounter {
  def dynamicRange: DynamicRange
  def sampleInterval: Duration
  def measurementUnit: MeasurementUnit

  def increment(): Unit
  def increment(times: Long): Unit
  def decrement(): Unit
  def decrement(times: Long): Unit
  def sample(): Unit
}


class PaddedMinMaxCounter(name: String, tags: Map[String, String], underlyingHistogram: Histogram with DistributionSnapshotInstrument,
    val sampleInterval: Duration) extends SnapshotableMinMaxCounter {

  private val min = new LongMaxUpdater(0L)
  private val max = new LongMaxUpdater(0L)
  private val sum = new AtomicLong()

  def dynamicRange: DynamicRange =
    underlyingHistogram.dynamicRange

  def measurementUnit: MeasurementUnit =
    underlyingHistogram.measurementUnit

  private[kamon] def snapshot(): MetricDistribution =
    underlyingHistogram.snapshot()

  def increment(): Unit =
    increment(1L)

  def increment(times: Long): Unit = {
    val currentValue = sum.addAndGet(times)
    max.update(currentValue)
  }

  def decrement(): Unit =
    decrement(1L)

  def decrement(times: Long): Unit = {
    val currentValue = sum.addAndGet(-times)
    min.update(-currentValue)
  }

  def sample(): Unit = {
    val currentValue = {
      val value = sum.get()
      if (value <= 0) 0 else value
    }

    val currentMin = {
      val rawMin = min.maxThenReset(-currentValue)
      if (rawMin >= 0)
        0
      else
        abs(rawMin)
    }

    val currentMax = max.maxThenReset(currentValue)

    underlyingHistogram.record(currentValue)
    underlyingHistogram.record(currentMin)
    underlyingHistogram.record(currentMax)
  }
}