package pw.binom.proxy.controllers

import pw.binom.metric.Metric
import pw.binom.metric.MetricProvider
import pw.binom.metric.prometheus.AbstractPrometheusHandler
import pw.binom.strong.injectServiceList

class PrometheusController : AbstractPrometheusHandler() {
    private val metricList by injectServiceList<Metric>()

    override suspend fun getMetrics() = metricList
}
