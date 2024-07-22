package io.github.raphiz.swisstopodownload

import org.http4k.client.JavaHttpClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.StructuredTaskScope
import javax.imageio.ImageIO
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

private const val ZOOM_LEVEL = 27
private const val URL_TEMPLATE =
    "https://wmts.geo.admin.ch/1.0.0/ch.swisstopo.pixelkarte-farbe/default/current/2056/$ZOOM_LEVEL/{x}/{y}.jpeg"

fun main() {
    // coordinate range - here the municipality of Seuzach as an example
    val xRange = 4294..4361
    val yRange = 1290..1342

    val tileCache = Path.of(".cache").also { Files.createDirectories(it) }
    val outputDirectory = Path.of("output").recreate()

    val coordinates = xRange.flatMap { x -> yRange.map { y -> x to y } }

    println("Downloading tiles...")
    downloadTiles(coordinates, tileCache)

    println("Combining tiles into a single large image...")
    val combinedImage = outputDirectory.resolve("combined.jpeg")
    combineImages(xRange, yRange, tileCache, combinedImage)

    val n = 3
    val m = 3
    println("Splitting up large image into a ${n}x$m grid...")
    splitImage(combinedImage, n, m, outputDirectory)

    println("Done! Files are stored in ${outputDirectory.absolutePathString()}")
}

private fun Path.recreate(): Path {
    if (this.exists()) {
        // Delete recursively
        Files.walk(this)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .forEach(File::delete)
    }
    Files.createDirectories(this)
    return this
}

fun downloadTiles(
    coordinates: List<Pair<Int, Int>>,
    outputDirectory: Path,
) {
    val client = JavaHttpClient()
    val executor = Executors.newVirtualThreadPerTaskExecutor()
    try {
        StructuredTaskScope.ShutdownOnFailure().use { scope ->
            coordinates.map { (x, y) ->
                scope.fork {
                    client.downloadTile(x, y, outputDirectory)
                }
            }

            scope.join()
            scope.throwIfFailed()
        }
    } finally {
        executor.shutdown()
    }
}

private fun HttpHandler.downloadTile(
    x: Int,
    y: Int,
    outputDirectory: Path,
) {
    val uri = URL_TEMPLATE.replace("{x}", "$x").replace("{y}", "$y")
    val outputFile = outputDirectory.resolve("${x}_$y.jpeg")
    if (!Files.exists(outputFile)) {
        println("Downloading x=$x y=$y")

        val request = Request(Method.GET, uri)
        val response = this(request)
        Files.copy(response.body.stream, outputFile)
    }
}

fun combineImages(
    xRange: IntRange,
    yRange: IntRange,
    outputDirectory: Path,
    destination: Path,
) {
    val firstImageFile = outputDirectory.resolve("${xRange.first}_${yRange.first}.jpeg")
    val firstImage = ImageIO.read(firstImageFile.toFile())

    val tileWidth = firstImage.width
    val tileHeight = firstImage.height

    val totalWidth = tileWidth * xRange.count()
    val totalHeight = tileHeight * yRange.count()

    val combinedImage = BufferedImage(totalWidth, totalHeight, BufferedImage.TYPE_INT_RGB)
    val g = combinedImage.createGraphics()

    for (x in xRange) {
        for (y in yRange) {
            val img = ImageIO.read(outputDirectory.resolve("${x}_$y.jpeg").toFile())
            val posX = (x - xRange.first) * tileWidth
            val posY = (y - yRange.first) * tileHeight
            g.drawImage(img, posX, posY, null)
        }
    }
    g.dispose()

    ImageIO.write(combinedImage, "jpeg", destination.toFile())
}

fun splitImage(
    srcFile: Path,
    n: Int,
    m: Int,
    outputDirectory: Path,
) {
    val image = ImageIO.read(srcFile.toFile())
    val subImageWidth = image.width / n
    val subImageHeight = image.height / m

    for (i in 0 until n) {
        for (j in 0 until m) {
            val subImage =
                image.getSubimage(
                    i * subImageWidth,
                    j * subImageHeight,
                    subImageWidth,
                    subImageHeight,
                )
            ImageIO.write(subImage, "jpeg", outputDirectory.resolve("${i + 1}_${j + 1}.jpeg").toFile())
        }
    }
}
