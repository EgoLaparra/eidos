package org.clulab.wm.eidos.groundings

import java.io.File
import java.util.{Collection => JCollection, Map => JMap}

import org.clulab.utils.Serializer
import org.clulab.processors.{Document, Processor, Sentence}
import org.clulab.processors.clu.CluProcessor
import org.clulab.processors.shallownlp.ShallowNLPProcessor
import org.clulab.wm.eidos.utils.FileUtils.getTextFromResource
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor

import scala.collection.JavaConverters._
import scala.collection.mutable

class OntologyNode(crumbs: Seq[String], name: String, polarity: Double, examples: Option[Seq[String]] = None, descriptions: Option[Seq[String]] = None) extends Serializable {

  protected def split(values: Option[Seq[String]]): Seq[String] =
      if (values.isEmpty) Seq.empty
      else values.get.flatMap(_.split(" +"))

  // There can already be a / in any of the stages of the route that must be escaped.
  // First, double up any existing backslashes, then escape the forward slashes with backslashes.
  protected def escapeRoute: Seq[String] =
      route.map(_.replace("\\", "\\\\").replace(OntologyNode.SEPARATOR, "\\" + OntologyNode.SEPARATOR))

  def path: String = escapeRoute.mkString(OntologyNode.SEPARATOR)

  val route = (name +: crumbs).reverse

  // Right now it doesn't matter where these come from, so they can be combined.
  val values: Seq[String] = split(examples) ++ split(descriptions)

  override def toString = path + " = " + values.toList
}

object OntologyNode {
  val SEPARATOR = "/"
}

class DomainOntology(val name: String, val ontologyNodes: Seq[OntologyNode]) extends Serializable {

  def iterateOntology(wordToVec: EidosWordToVec): Seq[ConceptEmbedding] =
      ontologyNodes.map { ontologyNode =>
        ConceptEmbedding(ontologyNode.path, wordToVec.makeCompositeVector(ontologyNode.values))
      }

  def save(filename: String) = {
    Serializer.save[DomainOntology](this, filename)
  }
}

object DomainOntology {
  val FIELD = "OntologyNode"
  val NAME = "name"
  val EXAMPLES = "examples"
  val DESCRIPTION = "descriptions"
  val POLARITY = "polarity"

  def serializedPath(name: String, dir: String): String = s"$dir/$name.serialized"

  // Load from serialized
  def load(name: String, cachedDir: String): DomainOntology = {
    val path = serializedPath(name, cachedDir)
    println(s"Loading serialized ontology from $path")
    Serializer.load[DomainOntology](path)
  }

  // This is mostly here to capture proc so that it doesn't have to be passed around.
  class DomainOntologyBuilder(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean) {

    def build(): DomainOntology = {

      val text = getTextFromResource(ontologyPath)
      val yaml = new Yaml(new Constructor(classOf[JCollection[Any]]))
      val yamlNodes = yaml.load(text).asInstanceOf[JCollection[Any]].asScala.toSeq
      val ontologyNodes = parseOntology(yamlNodes, Seq.empty, Seq.empty)

      new DomainOntology(name, ontologyNodes)
    }

    protected val getSentences: (String => Array[Sentence]) = proc match {
      // Earlier, a complete annotation was performed.
      // val sentences = proc.annotate(text).sentences
      // Now we just go through the POS tagging stage, but the procedure is
      // different for different kinds of processors.
      case proc: CluProcessor => (text => {
        val doc = proc.mkDocument(proc.preprocessText(text))

        // This is the key difference.  Lemmatization must happen first.
        proc.lemmatize(doc)
        proc.tagPartsOfSpeech(doc)
        doc.sentences
      })
      case proc: ShallowNLPProcessor => (text => {
        val doc = proc.mkDocument(proc.preprocessText(text))

        if (doc.sentences.nonEmpty)
          proc.tagPartsOfSpeech(doc)
        // Lemmatization, if needed, would happen afterwards.
        doc.sentences
      })
    }

    protected def realFiltered(text: String): Seq[String] = {
      val sentences = getSentences(text)

      sentences.flatMap { sentence =>
        sentence.words.zip(sentence.tags.get).filter { wordAndPos =>
          // Filter by POS tags which need to be kept (Nouns, Adjectives, and Verbs).
          wordAndPos._2.contains("NN") ||
            wordAndPos._2.contains("JJ") ||
            wordAndPos._2.contains("VB")
        }.map(_._1) // Get only the words.
      }
    }

    protected def fakeFiltered(text: String): Seq[String] = text.split(" +")

    protected val filtered: String => Seq[String] = if (filter) realFiltered else fakeFiltered

    protected def yamlNodesToStrings(yamlNodes: mutable.Map[String, JCollection[Any]], name: String): Option[Seq[String]] =
      yamlNodes.get(name).map(_.asInstanceOf[JCollection[String]].asScala.toSeq)

    protected def parseOntology(yamlNodes: mutable.Map[String, JCollection[Any]], ontologyNodes: Seq[OntologyNode],
        route: Seq[String]): Seq[OntologyNode] = {
      val name = yamlNodes.get(DomainOntology.NAME).get.asInstanceOf[String]
      val examples = yamlNodesToStrings(yamlNodes, DomainOntology.EXAMPLES)
      val descriptions = yamlNodesToStrings(yamlNodes, DomainOntology.DESCRIPTION)
      val polarity = yamlNodes.get(DomainOntology.POLARITY).get.asInstanceOf[Double]
      val filteredDescriptions =
          if (descriptions.isEmpty) descriptions
          else Some(descriptions.get.flatMap(filtered))

      ontologyNodes :+ new OntologyNode(route, name, polarity,  examples, filteredDescriptions)
    }

    protected def parseOntology(yamlNodes: Seq[Any], ontologyNodes: Seq[OntologyNode], crumbs: Seq[String]): Seq[OntologyNode] = {
      if (yamlNodes.nonEmpty) {
        val head = yamlNodes.head
        if (head.isInstanceOf[String])
          throw new Exception(s"Ontology has string (${head.asInstanceOf[String]}) where it should have a map.")
        val map: mutable.Map[String, JCollection[Any]] = head.asInstanceOf[JMap[String, JCollection[Any]]].asScala
        val key: String = map.keys.head
        val moreOntologyNodes =
            if (key == DomainOntology.FIELD) parseOntology(map, ontologyNodes, crumbs)
            else parseOntology(map(key).asScala.toSeq, ontologyNodes, key +: crumbs)

        parseOntology(yamlNodes.tail, moreOntologyNodes, crumbs)
      }
      else
        ontologyNodes
    }
  }

  def loadCachedOntology(name: String, ontologyPath: String, cachedDir: String): Option[DomainOntology] = {
    // Check to see if there is a cached ontology in the cached dir corresponding to the namespace
    // Get the timestamps of the files
    val cachedFile = new File(s"$cachedDir/$name.serialized")
    // If there is a cached file
    if (cachedFile.exists) {
      val textOntology = new File(ontologyPath)
      // Is there a yml version?
       if (textOntology.exists) {
        val cachedTimestamp = cachedFile.lastModified()
        val textTimestamp = textOntology.lastModified()
        if (cachedTimestamp > textTimestamp) {
          // If both versions exist, and the cached version is newer, load it
          return Some(DomainOntology.load(name, cachedDir))
        }
      } else {
        // If only a cached version exists, by all means load it!
        return Some(DomainOntology.load(name, cachedDir))
      }
    }

    // Otherwise, if there is no cached version, return None
    None
  }

  def apply(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean): DomainOntology = {
    // If there is a cached version which is newer than the ontology path, load it
    val loaded = loadCachedOntology(name, ontologyPath, cachedDir)
    if (loaded.nonEmpty) {
      return loaded.get
    }
    // else, build one
    new DomainOntologyBuilder(name, ontologyPath, cachedDir, proc, filter).build()
  }
}

// These are just here for when behavior might have to start differing.
object UNOntology {
  def apply(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean = true) = DomainOntology(name, ontologyPath, cachedDir, proc, filter)
}

object WDIOntology {
  def apply(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean = true) = DomainOntology(name, ontologyPath, cachedDir, proc, filter)
}

object FAOOntology {
  def apply(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean = true) = DomainOntology(name, ontologyPath, cachedDir, proc, filter)
}

object TopoFlowOntology {
  def apply(name: String, ontologyPath: String, cachedDir: String, proc: Processor, filter: Boolean = true) = DomainOntology(name, ontologyPath, cachedDir, proc, filter)
}
