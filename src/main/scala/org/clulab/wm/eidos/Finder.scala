package org.clulab.wm.eidos

import org.clulab.odin.{Actions, ExtractorEngine, Mention}
import org.clulab.processors.{Document, Processor}
import org.clulab.wm.eidos.actions.CorefHandler
import org.clulab.wm.eidos.document.AnnotatedDocument
import org.clulab.wm.eidos.groundings.{OntologyGrounder, OntologyGrounding}
import org.clulab.wm.eidos.mentions.EidosMention
import org.clulab.wm.eidos.utils.DocumentFilter

import scala.util.matching.Regex

// -------------------------------
//            Whole System
// -------------------------------

// OR -- is this too much -- better handled in an App??
class EidosSystem(reader: EidosReader, postProcessor: PostProcessor, serializer: EidosSerializer) {

  def findFiles(s: String): Seq[String] = ??? // fixme, placeholder
  def runExtraction(collectionDir: String, outputDir: String) = {
    val annotated = for {
      f <- findFiles(collectionDir)
      text <- scala.io.Source.fromFile(f).getLines().toArray // fixme, placeholder
      mentions = reader.extract(text)
    } yield postProcessor.process(mentions)
    serializer.serialize(annotated, outputDir)
  }
  // todo: better versions of input/output
  def runExtractionFile(inputFile: String, outputFile: String) = {
    ??? // maybe...?
  }
}



// -------------------------------
//            Reader
// -------------------------------

class EidosReader ( // or do we call it EidosSystem??
  entityFinders: Seq[Finder],       // 0 or more
  eventFinders: Seq[Finder],        // 0 or more?
  preProcessor: PreProcessor,       // in case you need to parse stuff
  contentManager: ContentManager    // ??? to filter empty stuff before you give it to post-processing
) extends Finder {

  def extract(doc: Document): Seq[Mention] = ???
  def extract(text: String): Seq[Mention] = ???

}





// -------------------------------
//            Finders
// -------------------------------

trait Finder {
  def extract(doc: Document): Seq[Mention]
  def extract(text: String): Seq[Mention]
}

class RuleBasedEntityFinder(expander: Option[Expander], val entityEngine: ExtractorEngine, val avoidEngine: ExtractorEngine) extends Finder {
  def extract(doc: Document): Seq[Mention] = ???
  def extract(text: String): Seq[Mention] = ???
}

class GazetteerEntityFinder(lexicons: Seq[String], expander: Option[Expander]) extends Finder {
  def extract(doc: Document): Seq[Mention] = ???
  def extract(text: String): Seq[Mention] = ???
}

class EventFinder(eventEngine: ExtractorEngine) extends Finder {
  def extract(doc: Document): Seq[Mention] = ???
  def extract(text: String): Seq[Mention] = ???
}


class EidosActions(expander: Option[Expander], corefHandler: Option[CorefHandler]) extends Actions


// -------------------------------
//         Preprocessing
// -------------------------------


trait PreProcessor {
  def parse(text: String): Document
}

// filter sentences and parse...
class EidosPreprocessor(documentFilter: Option[DocumentFilter], processor: Processor) extends PreProcessor {
  def parse(text: String): Document = ???
}


// -------------------------------
//            Expanders
// -------------------------------


trait Expander {
  def expand(ms: Seq[Mention]): Seq[Mention]
}

class EntityExpander(validLabels: Set[String], dependencies: Dependencies) extends Expander {
  def expand(ms: Seq[Mention]): Seq[Mention] = ???
}

class ArgumentExpander(validArgs: Set[String], validLabels: Set[String], dependencies: Dependencies) extends Expander {
  def expand(ms: Seq[Mention]): Seq[Mention] = ???
}
case class Dependencies(validIncoming: Set[Regex], invalidIncoming: Set[Regex], validOutgoing: Set[Regex], invalidOutgoing: Set[Regex])



// -------------------------------
//         ContentManagers
// -------------------------------


// TODO: remove -- put in an object, the one method `isCanonical` is generic + stopwords,
//trait Canonicalizer {
//
//}

trait ContentManager {
  def isContent(s: String): Boolean
}

class StopTransparentContentManager(stopWords: Set[String], transparentWords: Set[String]) extends ContentManager {
  def isContent(s: String): Boolean = !isStop(s) && !isTransparent(s)
  def isStop(s:String): Boolean = stopWords.contains(s)
  def isTransparent(s: String): Boolean = transparentWords.contains(s)
}



// -------------------------------
//         PostProcessing
// -------------------------------

trait PostProcessingStep {
  def process(inputs:Seq[EidosMention]):Seq[EidosMention]
}

class PostProcessor(steps: Seq[PostProcessingStep], serializer: EidosSerializer) {
  def toEidosMention(m: Mention): EidosMention = ???

  def process(mentions: Seq[Mention]): AnnotatedDocument = {
    val eidosMentions = mentions.map(toEidosMention)
    //
    // now apply all the additional non-Antlr steps such as solving contractions, normalization, post-processing
    //
    var postProcessedMentions = eidosMentions
    for(step <- steps) {
      postProcessedMentions = step.process(postProcessedMentions)
    }
    // todo: is this null ok?
    AnnotatedDocument(mentions.headOption.map(_.document).orNull, mentions, postProcessedMentions)
  }
  // or something like this...
  def serialize(annotatedDocuments: Seq[AnnotatedDocument], filename: String) = ???

}

class CanonicalizerStep(contentManager: ContentManager) extends PostProcessingStep {
  // Make the canonical name
  def process(inputs:Seq[EidosMention]):Seq[EidosMention] = ???
}

class GroundingStep(ontologies: Seq[OntologyGrounder]) extends PostProcessingStep {
  // Add the grounding, based on the canonical name
  def process(inputs:Seq[EidosMention]):Seq[EidosMention] = ???
}


//trait ddd {
//  val isPrimary: Boolean
//
//  def groundOntology(mention: EidosMention, previousGroundings: Option[Map[String, OntologyGrounding]]): OntologyGrounding
//  def groundOntology(mention: EidosMention): OntologyGrounding = groundOntology(mention, None)
//  def groundOntology(mention: EidosMention, previousGroundings: Map[String, OntologyGrounding]): OntologyGrounding = groundOntology(mention, Some(previousGroundings))
//
//  def groundable(mention: EidosMention, previousGroundings: Option[Map[String, OntologyGrounding]]): Boolean
//  def groundable(mention: EidosMention): Boolean = groundable(mention, None)
//  def groundable(mention: EidosMention, previousGroundings: Map[String, OntologyGrounding]): Boolean = groundable(mention, Some(previousGroundings))
//
//}


// -------------------------------
//            Exporting
// -------------------------------



trait EidosSerializer {
  // and some other versions for File, etc.
  def serialize(annotatedDocuments: Seq[AnnotatedDocument], filename: String): Unit
}

class JLDSerializer extends EidosSerializer {
  override def serialize(annotatedDocuments: Seq[AnnotatedDocument], filename: String): Unit = ???
}
// For the vanilla odin format used in PT influence search
class OdinClassicInfluenceSerializer extends EidosSerializer {
  override def serialize(annotatedDocuments: Seq[AnnotatedDocument], filename: String): Unit = ???
}