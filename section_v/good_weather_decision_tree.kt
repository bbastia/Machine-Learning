import org.nield.kotlinstatistics.random
import java.net.URL
import kotlin.math.pow


data class WeatherItem(val rain: Double, val lightning: Double, val cloudy: Double, val temperature: Double, val goodWeatherInd: Double? = null)


val data = URL("https://raw.githubusercontent.com/thomasnield/machine-learning-demo-data/master/classification/good_weather_classification.csv")
        .readText().split(Regex("\\r?\\n"))
        .asSequence()
        .drop(1)
        .filter { it.isNotBlank() }
        .map { s ->
            s.split(",").map { it.toDouble() }
        }
        .map { WeatherItem(it[0],it[1],it[2],it[3],it[4]) }
        .toList()


class Feature(val name: String, val mapper: (WeatherItem) -> Double) {
    override fun toString() = name
}

val features = listOf(
        Feature("Rain") { it.rain },
        Feature("Lightning") { it.lightning },
        Feature("Cloudy") { it.cloudy },
        Feature("Temperature") { it.temperature }
)

fun giniImpurity(samples: List<WeatherItem>): Double {

    val totalSampleCount = samples.count().toDouble()

    return 1.0 - (samples.count { it.goodWeatherInd == 1.0 }.toDouble() / totalSampleCount).pow(2) -
            (samples.count { it.goodWeatherInd == 0.0 }.toDouble() / totalSampleCount).pow(2)
}

fun giniImpurityForSplit(feature: Feature, splitValue: Double, samples: List<WeatherItem>): Double {
    val positiveFeatureSamples = samples.filter { feature.mapper(it) >= splitValue }
    val negativeFeatureSamples = samples.filter { feature.mapper(it) < splitValue }

    val positiveImpurity = giniImpurity(positiveFeatureSamples)
    val negativeImpurity = giniImpurity(negativeFeatureSamples)

    return (positiveImpurity * (positiveFeatureSamples.count().toDouble() / samples.count().toDouble())) +
            (negativeImpurity * (negativeFeatureSamples.count().toDouble() / samples.count().toDouble()))
}

fun splitContinuousVariable(feature: Feature, samples: List<WeatherItem>): Double? {

    val featureValues = samples.asSequence().map { feature.mapper(it) }.distinct().toList().sorted()

    val bestSplit = featureValues.asSequence().zipWithNext { value1, value2 -> (value1 + value2) / 2.0 }
            .minBy { giniImpurityForSplit(feature, it, samples) }

    return bestSplit
}

data class FeatureAndSplit(val feature: Feature, val split: Double)

fun buildLeaf(samples: List<WeatherItem>, previousLeaf: TreeLeaf? = null, featureSampleSize: Int? = null ): TreeLeaf? {

    val fs = (if (featureSampleSize == null) features else features.random(featureSampleSize) )
            .asSequence()
            .filter { splitContinuousVariable(it, samples) != null }
            .map { feature ->
                FeatureAndSplit(feature, splitContinuousVariable(feature, samples)!!)
            }.minBy { (feature, split) ->
                giniImpurityForSplit(feature, split, samples)
            }

    return if (previousLeaf == null ||
            (fs != null && giniImpurityForSplit(fs.feature, fs.split, samples) < previousLeaf.giniImpurity))
        TreeLeaf(fs!!.feature, fs.split, samples)
    else
        null
}

class TreeLeaf(val feature: Feature,
               val splitValue: Double,
                val samples: List<WeatherItem>) {

    val goodWeatherItems = samples.filter { it.goodWeatherInd == 1.0 }
    val badWeatherItems = samples.filter { it.goodWeatherInd == 0.0 }

    val giniImpurity = giniImpurityForSplit(feature, splitValue, samples)

    val positiveItems = samples.filter { feature.mapper(it) >= splitValue }
    val negativeItems = samples.filter { feature.mapper(it) < splitValue }

    val featurePositiveLeaf: TreeLeaf? = buildLeaf(positiveItems, this)
    val featureNegativeLeaf: TreeLeaf? = buildLeaf(negativeItems, this)

    fun predict(weatherItem: WeatherItem): Double {

        val featureValue = feature.mapper(weatherItem)

        return when {
            featureValue >= splitValue -> when {
                featurePositiveLeaf == null -> (goodWeatherItems.count { feature.mapper(it) >= splitValue }.toDouble() / samples.count { feature.mapper(it) >= splitValue }.toDouble() )
                else -> featurePositiveLeaf.predict(weatherItem)
            }
            else -> when {
                featureNegativeLeaf == null -> (goodWeatherItems.count { feature.mapper(it) < splitValue }.toDouble() / samples.count { feature.mapper(it) < splitValue }.toDouble() )
                else -> featureNegativeLeaf.predict(weatherItem)
            }
        }
    }
    override fun toString() = "$feature split on $splitValue, ${negativeItems.count()}|${positiveItems.count()}, Impurity: $giniImpurity"
}

fun recurseAndPrintTree(leaf: TreeLeaf?, depth: Int = 0) {

    if (leaf != null) {
        println("\t".repeat(depth) + "($leaf)")
        recurseAndPrintTree(leaf.featureNegativeLeaf, depth + 1)
        recurseAndPrintTree(leaf.featurePositiveLeaf, depth + 1)
    }
}


fun main() {

    // Decision tree

    val tree = buildLeaf(data)

    recurseAndPrintTree(tree)

    val prediction = tree!!.predict(WeatherItem(rain=0.0, lightning = 0.0, cloudy = 1.0, temperature = 76.0))

    if (prediction >= .5) {
        println("Weather is good: ${prediction * 100.0}% confident")
    } else {
        println("Weather is bad: ${prediction * 100.0}% chance of it being good ")
    }


    // Random forest

/*    val randomForest = (1..300).map { buildLeaf(samples = data.random((50 / 3) * 2), featureSampleSize = 2)!! }

    val input = WeatherItem(rain=0.0, cloudy = 1.0, lightning = 0.0, temperature = 81.0)

    val vote = randomForest.count { it.predict(input) >= .5 }

    if (vote >= 150) {
        println("Weather is good: $vote/300 votes")
    } else {
        println("Weather is bad:  $vote/300 votes")
    }*/
}