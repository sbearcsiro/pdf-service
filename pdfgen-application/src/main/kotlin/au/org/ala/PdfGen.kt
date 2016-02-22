package au.org.ala


import au.org.ala.resources.PdfResource
import au.org.ala.services.PdfService
import io.dropwizard.Application
import io.dropwizard.client.HttpClientBuilder
import io.dropwizard.forms.MultiPartBundle
import io.dropwizard.setup.Bootstrap
import io.dropwizard.setup.Environment
import org.eclipse.jetty.servlets.CrossOriginFilter
import org.eclipse.jetty.servlets.CrossOriginFilter.*
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.util.*
import javax.servlet.DispatcherType
import kotlin.properties.Delegates

public class PdfGen : Application<PdfGenConfiguration>() {

    companion object {

        val log = LoggerFactory.getLogger(PdfGen::class.java)

        val ALLOWED_ORIGINS = "*"

        val pdfGen = PdfGen()

        //throws(Exception::class)
        @JvmStatic fun main(args: Array<String>) {
            pdfGen.run(*args)
        }

        //throws(Exception::class)
        @Suppress("UNUSED_SYMBOL")
        @JvmStatic fun stop(args: Array<String>) {
            pdfGen.shutdown(*args)
        }
    }

    var environment: Environment by Delegates.notNull()

    override fun initialize(bootstrap: Bootstrap<PdfGenConfiguration>) {
        log.info("Initialising")
        super.initialize(bootstrap)
        bootstrap.addBundle(MultiPartBundle())
    }

    override fun run(config: PdfGenConfiguration, environment: Environment) {
        this.environment = environment

        val filter = environment.servlets().addFilter("CORSFilter", CrossOriginFilter::class.java)

        filter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, environment.applicationContext.contextPath + "*")
        filter.setInitParameter(ALLOWED_METHODS_PARAM, "HEAD,GET,PUT,POST,DELETE,OPTIONS")
        filter.setInitParameter(ALLOWED_ORIGINS_PARAM, ALLOWED_ORIGINS)
        filter.setInitParameter(ALLOWED_HEADERS_PARAM, "Origin, Content-Type, Accept")
        filter.setInitParameter(ALLOW_CREDENTIALS_PARAM, "true")

        val storageDir = ensureStorageDir(config.storageDir)
        log.info("Using ${storageDir.absolutePath} for PDF storage")

        val httpClient = HttpClientBuilder(environment).using(config.httpClientConfiguration).build("httpClient")
        val service = PdfService(config.sofficePath, storageDir)
        //environment.jersey().register(KtPdfResource(httpClient, service, config.urlCacheSpec))
        environment.jersey().register(PdfResource(httpClient, service, config.urlCacheSpec))

    }

    private fun ensureStorageDir(storageDir: String): File {
        return File(storageDir).apply {
            if (exists()) {
                if (!isDirectory) throw IOException("Storage dir is not a directory: $absolutePath")
                if (!canWrite()) throw IOException("Storage dir is not writable: $absolutePath")
            } else if (!mkdirs()) {
                throw IOException("Could not create storage dir: $absolutePath")
            }
        }
    }

    fun shutdown(vararg args: String) {
        log.info("Stopping pdfgen dropwizard")
        log.debug("Got args ${args.joinToString(",")}")

        log.info("Stopping admin context")
        environment.adminContext.stop()
        log.info("Stopping application context")
        environment.applicationContext.stop()
        log.info("Stopping health checks")
        environment.healthCheckExecutorService.shutdownNow()
        log.info("Stopping lifecycle managed objects")
        for (lc in environment.lifecycle().managedObjects.asReversed()) {
            log.info("Stopping $lc")
            lc.stop()
        }

        log.info("Quitting VM")
        System.exit(0)
    }
}
