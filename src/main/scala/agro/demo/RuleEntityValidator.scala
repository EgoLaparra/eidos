//package agro.demo
//
//import org.clulab.processors.Sentence
//
///**
//  * Validates if the span identified as an entity is actually valid
//  * User: mihais
//  * Date: 10/24/16
//  */
//class RuleEntityValidator(val sentence:Sentence, val knownCaseInsensitives:Set[String]) extends EntityValidator {
//
//  def validMatch(start:Int, end:Int):Boolean = {
//    if(start >= end)
//      return false
//
//    // must contain at least one NN*
//    // see also removeSinglePrepositions, for deprecated code
//    var nouns = 0
//    for(i <- start until end)
//      if(sentence.tags.get(i).startsWith("NN"))
//        nouns += 1
//    if(nouns == 0)
//      return false
//
//    // some entities end with -ing verbs (e.g., "binding")
//    // do not accept them when followed by "to"
//    // TODO: anything else?
//    if(end < sentence.words.length) {
//      val last = sentence.words(end - 1)
//      val to = sentence.words(end)
//      if(last.length > 3 && last.toLowerCase.endsWith("ing") && to.toLowerCase == "to") {
//        return false
//      }
//    }
//
//    // the text must contain at least one letter AND (the letter must be upper case OR the text contains at least 1 digit)
//    val text = sentence.getSentenceFragmentText(start, end)
//    val (letters, digits, upperCaseLetters, spaces) = scanText(text)
//    if(letters > 0 && (digits > 0 || upperCaseLetters > 0 || spaces > 0)) {
//      //println("Found valid match: " + text)
//      return true
//    }
//
//    // have we seen this single token as lower case in the KB; if so, accept it in the text
//    if(letters > 0 && knownCaseInsensitives.contains(text)) {
//      return true
//    }
//
//    // if at least 1 letter and length > 3 accept (e.g., "rapamycin")
//    if(letters > 0 && text.length > 3)
//      return true
//
//    false
//  }
//
//  private def scanText(text:String):(Int, Int, Int, Int) = {
//    var letters = 0
//    var digits = 0
//    var upperCaseLetters = 0
//    var spaces = 0
//    for(i <- text.indices) {
//      val c = text.charAt(i)
//      if(Character.isLetter(c)) letters += 1
//      if(Character.isUpperCase(c)) upperCaseLetters += 1
//      if(Character.isDigit(c)) digits += 1
//      if(Character.isWhitespace(c)) spaces += 1
//    }
//    (letters, digits, upperCaseLetters, spaces)
//  }
//}
