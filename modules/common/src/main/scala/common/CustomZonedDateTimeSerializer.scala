package common

import org.json4s._
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// 1. Készítünk egy egyedi szerializálót, ami támogatja az ezredmásodperces ISO formátumot is
object CustomZonedDateTimeSerializer extends CustomSerializer[ZonedDateTime](format => (
  {
    // Deszerializáció (JSON -> ZonedDateTime)
    case JString(s) =>
      try {
        ZonedDateTime.parse(s, DateTimeFormatter.ISO_ZONED_DATE_TIME)
      } catch {
        case e: Exception => throw new MappingException(s"Hibás dátum formátum: $s", e)
      }
    case JNull => null
  },
  {
    // Szerializáció (ZonedDateTime -> JSON)
    case d: ZonedDateTime => JString(d.format(DateTimeFormatter.ISO_ZONED_DATE_TIME))
  }
))
