// SPDX-FileCopyrightText: 2020 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import se.svt.oss.encore.config.EncoreProperties

@Configuration
class SchedulingConfiguration {

    @Bean
    fun scheduler(encoreProperties: EncoreProperties): ThreadPoolTaskScheduler {
        val taskScheduler = ThreadPoolTaskScheduler()
        taskScheduler.poolSize = encoreProperties.concurrency
        taskScheduler.threadNamePrefix = "scheduling-"
        taskScheduler.setAwaitTerminationSeconds(6)
        return taskScheduler
    }
}
