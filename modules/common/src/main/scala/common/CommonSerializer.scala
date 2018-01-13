package common

import com.typesafe.config.ConfigFactory
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.zip.{Deflater, Inflater}
import javax.crypto.{Cipher, SecretKeyFactory}
import javax.crypto.spec.{IvParameterSpec, PBEKeySpec, SecretKeySpec}
import org.json4s._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}
import scala.collection.mutable.ArrayBuilder


case class FunctionLink(order: Int, url: String, title: String)
  extends Ordered[FunctionLink]
{
  def compare(that: FunctionLink) = order.compare(that.order)
}

object CommonSerializer extends JsonSerializer {
	def identifier = 0xfeca

	implicit val formats = getFormats()

  def getFormats(ls: List[Class[_]] = Nil): Formats = new DefaultFormats {
    override val typeHintFieldName = "_t"
    override val typeHints = ShortTypeHints(
			classOf[FunctionLink] ::
			classOf[Stat] ::
      ls
    )
	} ++ org.json4s.ext.JodaTimeSerializers.all + LocalDateSerializer

  def read[T: Manifest](data: Array[Byte])
    (implicit formats: Formats, transformation: Transformation) =
    Serialization.read[T](new String(transformation.fromBinary(data)))

  def write(data: AnyRef)
    (implicit formats: Formats, transformation: Transformation) =
    transformation.toBinary(Serialization.write(data).getBytes)

  def pipeTransformations(transformations: Transformation*): Transformation = {
    new Transformation {
      val toPipeline =
        transformations.map(x => x.toBinary _).reduceLeft(_ andThen _)
      val fromPipeline =
        transformations.map(x => x.fromBinary _).reverse.reduceLeft(_ andThen _)

      def toBinary(inputBuff: Array[Byte]): Array[Byte] = toPipeline(inputBuff)
      def fromBinary(inputBuff: Array[Byte]): Array[Byte] = fromPipeline(inputBuff)
    }
  }

  trait Transformation {
    def toBinary(inputBuff: Array[Byte]): Array[Byte]
    def fromBinary(inputBuff: Array[Byte]): Array[Byte]
  }

  object EmptyTransformation extends Transformation {
    def toBinary(inputBuff: Array[Byte]) = inputBuff
    def fromBinary(inputBuff: Array[Byte]) = inputBuff
  }

  object ZipCompressor extends Transformation {

    lazy val deflater = new Deflater(Deflater.BEST_SPEED)
    lazy val inflater = new Inflater()

    def toBinary(inputBuff: Array[Byte]): Array[Byte] = {
      val inputSize = inputBuff.length
      val outputBuff = new ArrayBuilder.ofByte
      outputBuff += (inputSize & 0xff).toByte
      outputBuff += (inputSize >> 8 & 0xff).toByte
      outputBuff += (inputSize >> 16 & 0xff).toByte
      outputBuff += (inputSize >> 24 & 0xff).toByte

      deflater.setInput(inputBuff)
      deflater.finish()
      val buff = new Array[Byte](4096)

      while (!deflater.finished) {
        val n = deflater.deflate(buff)
        outputBuff ++= buff.take(n)
      }
      deflater.reset()
      outputBuff.result
    }

    def fromBinary(inputBuff: Array[Byte]): Array[Byte] = {
      val size: Int = (inputBuff(0).asInstanceOf[Int] & 0xff) |
        (inputBuff(1).asInstanceOf[Int] & 0xff) << 8 |
        (inputBuff(2).asInstanceOf[Int] & 0xff) << 16 |
        (inputBuff(3).asInstanceOf[Int] & 0xff) << 24
      val outputBuff = new Array[Byte](size)
      inflater.setInput(inputBuff, 4, inputBuff.length - 4)
      inflater.inflate(outputBuff)
      inflater.reset()
      outputBuff
    }
  }

  class Cryptographer(password: String, mode: String) extends Transformation {
    val config = ConfigFactory.load()
    private[this] val salt = config.getString("serializer.saltKey").getBytes
    private[this] val ivLength = 16
    private[this] val spec =
      new PBEKeySpec(password.toCharArray(), salt, 65536, 128)
    private[this] val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    private[this] val key = f.generateSecret(spec).getEncoded
    private[this] val sKeySpec = new SecretKeySpec(key, "AES")
    private[this] var iv: Array[Byte] = Array.fill[Byte](ivLength)(0)
    private lazy val random = new SecureRandom()

    def encrypt(plainTextBytes: Array[Byte]): Array[Byte] = {
      val cipher = Cipher.getInstance(mode)
      random.nextBytes(iv)
      val ivSpec = new IvParameterSpec(iv)
      cipher.init(Cipher.ENCRYPT_MODE, sKeySpec, ivSpec)
      iv ++ cipher.doFinal(plainTextBytes)
    }

    def decrypt(encryptedBytes: Array[Byte]): Array[Byte] = {
      val cipher = Cipher.getInstance(mode)
      val ivSpec = new IvParameterSpec(encryptedBytes, 0, ivLength)
      cipher.init(Cipher.DECRYPT_MODE, sKeySpec, ivSpec)
      cipher.doFinal(encryptedBytes, ivLength, encryptedBytes.length - ivLength)
    }

    override def toBinary(inputBuff: Array[Byte]): Array[Byte]  = {
      encrypt(inputBuff)
    }
    override def fromBinary(inputBuff: Array[Byte]): Array[Byte] = {
      decrypt(inputBuff)
    }
  }
}

case object LocalDateSerializer extends CustomSerializer[LocalDate](format =>
( {
  case JString(s) =>
    LocalDate.parse(s)
}, {
  case date: LocalDate =>
    JString(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
}))
