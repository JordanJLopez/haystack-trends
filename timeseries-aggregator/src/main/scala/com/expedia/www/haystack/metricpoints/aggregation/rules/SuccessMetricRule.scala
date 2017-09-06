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

package com.expedia.www.haystack.metricpoints.aggregation.rules

import com.expedia.www.haystack.metricpoints.aggregation.metrics.AggregationType
import com.expedia.www.haystack.metricpoints.aggregation.metrics.AggregationType.AggregationType
import com.expedia.www.haystack.metricpoints.entities.{MetricPoint, MetricType}

trait SuccessMetricRule extends MetricRule {
  override def isMatched(metricPoint: MetricPoint): Option[AggregationType] = {
    if (metricPoint.metric.toLowerCase.contains("success-spans") && metricPoint.`type`.equals(MetricType.Gauge)) {
      Some(AggregationType.Count)
    } else {
      super.isMatched(metricPoint)
    }
  }
}
