// SPDX-FileCopyrightText: 2025 Sveriges Television AB
//
// SPDX-License-Identifier: EUPL-1.2

package se.svt.oss.encore.redis

import io.github.oshai.kotlinlogging.KotlinLogging
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.json.JsonPath
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.lettuce.core.search.arguments.CreateArgs
import io.lettuce.core.search.arguments.NumericFieldArgs
import io.lettuce.core.search.arguments.SearchArgs
import io.lettuce.core.search.arguments.SortByArgs
import io.lettuce.core.search.arguments.TagFieldArgs
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import se.svt.oss.encore.cancellation.CancellationListener
import se.svt.oss.encore.cancellation.SegmentProgressListener
import se.svt.oss.encore.config.RedisProperties
import se.svt.oss.encore.model.CancelEvent
import se.svt.oss.encore.model.EncoreJob
import se.svt.oss.encore.model.SegmentProgressEvent
import se.svt.oss.encore.model.Status
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.util.UUID

private val log = KotlinLogging.logger {}

private val escapeTagCharsRegex = Regex("(?=[]\\[|(){}'-])")

@Service
class RedisService(
    jsonMapperBuilder: JsonMapper.Builder,
    private val redisProperties: RedisProperties,
    private val redisConnection: StatefulRedisConnection<String, String>,
    private val cancelPubSubConnection: StatefulRedisPubSubConnection<String, CancelEvent>,
    private val segmentProgressPubSubConnection: StatefulRedisPubSubConnection<String, SegmentProgressEvent>,
) {
    private val jsonMapper = jsonMapperBuilder
        .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DateTimeFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
        .disable(DateTimeFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
        .build()
    private val jobPrefix = "${redisProperties.prefix}:encoreJobs:"
    private val indexKey = "${redisProperties.prefix}:encoreJobsIdx_v1"
    private val cancelChannelPrefix = "${redisProperties.prefix}:cancel-channel:"
    private val segmentChannelPrefix = "${redisProperties.prefix}:segment-progress-channel:"
    private val indexLockKey = "${redisProperties.prefix}:index-lock"
    private val sortableFields = setOf("createdDate", "priority")

    fun createIndex() {
        val commands = redisConnection.sync()
        if (!commands.ftList().contains(indexKey)) {
            log.debug { "Index $indexKey does not exist" }
            val lockValue = UUID.randomUUID().toString()
            val setArgs = SetArgs.Builder.nx().px(Duration.ofSeconds(30))
            val reply = commands.set(indexLockKey, lockValue, setArgs)
            if (reply == "OK") {
                log.debug { "Acquired lock" }
                try {
                    val createArgs = CreateArgs.builder<String, String>()
                        .on(CreateArgs.TargetType.JSON)
                        .withPrefix(jobPrefix)
                        .build()
                    val fieldArgs = listOf(
                        TagFieldArgs.builder<String>().name("$.status").`as`("status").build(),
                        NumericFieldArgs.builder<String>().name("$.createdDate").`as`("createdDate").sortable().build(),
                        NumericFieldArgs.builder<String>().name("$.priority").`as`("priority").sortable().build(),
                        TagFieldArgs.builder<String>().name("$.baseName").`as`("baseName").build(),
                        TagFieldArgs.builder<String>().name("$.profile").`as`("profile").build(),
                        TagFieldArgs.builder<String>().name("$.externalId").`as`("externalId").build(),
                    )
                    if (commands.ftCreate(indexKey, createArgs, fieldArgs) != "OK") {
                        throw RuntimeException("Failed to create index!")
                    }
                    log.info { "Created index!" }
                } finally {
                    val unlockSha = commands.scriptLoad(
                        """
                        if redis.call("get",KEYS[1]) == ARGV[1] then
                            return redis.call("del",KEYS[1])
                        else
                            return 0
                        end
                        """.trimIndent(),
                    )
                    val res = commands.evalsha<Long>(
                        unlockSha,
                        ScriptOutputType.INTEGER,
                        arrayOf(indexLockKey),
                        lockValue,
                    )
                    if (res < 1) {
                        log.warn { "Failed to unlock!" }
                    }
                }
            }
        }
    }

    fun findByIdOrNull(id: UUID): EncoreJob? = redisConnection.sync()
        .jsonGetRaw("${jobPrefix}$id", JsonPath.ROOT_PATH)
        .firstOrNull()
        ?.let { jsonMapper.readValue<List<EncoreJob>>(it).firstOrNull() }

    fun updateProgress(id: UUID, progress: Int) {
        val commands = redisConnection.sync()
        val key = "$jobPrefix$id"
        commands
            .jsonSet(
                key,
                JsonPath.of("$.progress"),
                "$progress",
            )
        commands.expire(key, redisProperties.jobExpireTime)
    }

    fun save(job: EncoreJob) {
        val commands = redisConnection.sync()
        val key = "$jobPrefix${job.id}"
        commands
            .jsonSet(
                key,
                JsonPath.ROOT_PATH,
                jsonMapper.writeValueAsString(job),
            )
        commands.expire(key, redisProperties.jobExpireTime)
    }

    fun findAll(pageable: Pageable): Page<EncoreJob> = findByQuery("*", pageable)

    fun findByStatus(status: Status, pageable: Pageable): Page<EncoreJob> = findByQuery("@status:{$status}", pageable)

    fun findByExternalId(externalId: String, pageable: Pageable): Page<EncoreJob> =
        findByQuery("@externalId:{${escapeTag(externalId)}}", pageable)

    fun findByProfile(profile: String, pageable: Pageable): Page<EncoreJob> =
        findByQuery("@profile:{${escapeTag(profile)}}", pageable)

    fun findByBaseName(baseName: String, pageable: Pageable): Page<EncoreJob> =
        findByQuery("@baseName:{${escapeTag(baseName)}}", pageable)

    private fun escapeTag(tag: String): String = tag.replace(escapeTagCharsRegex, "\\\\")

    private fun findByQuery(query: String, pageable: Pageable): Page<EncoreJob> {
        val searchArgs = searchArgs(pageable)
        val commands = redisConnection.sync()
        val searchReply = commands.ftSearch(indexKey, query, searchArgs)
        val count = searchReply.count
        val jobs = searchReply.results.mapNotNull { res ->
            res.fields["$"]?.let { jsonMapper.readValue<EncoreJob>(it) }
        }
        return PageImpl(jobs, pageable, count)
    }

    private fun searchArgs(pageable: Pageable): SearchArgs<String, String> {
        val builder = SearchArgs.builder<String, String>()
            .limit(pageable.offset, pageable.pageSize.toLong())
        val sort = pageable.getSortOr(Sort.by("createdDate").descending())
        val sortBuilder = SortByArgs.builder<String>()
        if (sort.count() > 1) {
            throw InvalidQueryException("Illegal sort: $sort. Only one sort property is allowed.")
        }
        val order = sort.toList().first()
        if (order.property !in sortableFields) {
            throw InvalidQueryException("Sorting by ${order.property} is not allowed! Allowed fields: $sortableFields")
        }
        sortBuilder.attribute(order.property)
        if (order.isDescending) {
            sortBuilder.descending()
        }
        return builder.sortBy(sortBuilder.build()).build()
    }

    fun addCancelListener(listener: CancellationListener) {
        val encoreJobId = listener.encoreJobId
        log.debug { "Adding cancel listener for job $encoreJobId" }
        cancelPubSubConnection.addListener(listener)
        cancelPubSubConnection.sync().ssubscribe("$cancelChannelPrefix$encoreJobId")
    }

    fun cancel(jobId: UUID): Long {
        log.debug { "Sending cancel event for job $jobId" }
        val received = cancelPubSubConnection.sync().spublish("$cancelChannelPrefix$jobId", CancelEvent(jobId))
        log.debug { "$received listener(s) received cancel event" }
        return received
    }

    fun removeCancelListener(listener: CancellationListener) {
        log.debug { "Removing cancel listener for job ${listener.encoreJobId}" }
        cancelPubSubConnection.removeListener(listener)
        cancelPubSubConnection.sync().sunsubscribe("$cancelChannelPrefix${listener.encoreJobId}")
    }

    fun addSegmentProgressListener(listener: SegmentProgressListener) {
        log.debug { "Adding segment progress listener for ${listener.encoreJobId}" }
        segmentProgressPubSubConnection.addListener(listener)
        segmentProgressPubSubConnection.sync().ssubscribe("$segmentChannelPrefix${listener.encoreJobId}")
    }

    fun removeSegmentProgressListener(listener: SegmentProgressListener) {
        log.debug { "Removing segment progress listener for ${listener.encoreJobId}" }
        segmentProgressPubSubConnection.removeListener(listener)
        segmentProgressPubSubConnection.sync().sunsubscribe("$segmentChannelPrefix${listener.encoreJobId}")
    }

    fun sendProgress(event: SegmentProgressEvent) {
        log.debug { "Sending segment progress event for ${event.jobId}" }
        val received = segmentProgressPubSubConnection.sync().spublish("$segmentChannelPrefix${event.jobId}", event)
        log.debug { "$received listener(s) received segment progress event" }
    }
}
