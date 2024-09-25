package pw.binom.proxy.server

import pw.binom.metric.MetricProvider
import pw.binom.metric.prometheus.AbstractPrometheusHandler
import pw.binom.strong.injectServiceList

class PrometheusController : AbstractPrometheusHandler() {
    private val metricList by injectServiceList<MetricProvider>()

    override suspend fun getMetrics(): List<MetricProvider> = metricList
}
