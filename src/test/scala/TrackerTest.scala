import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.karasiq.bittorrent.announce.{HttpTracker, TrackerRequest, TrackerResponse}
import com.karasiq.bittorrent.dispatcher._
import com.karasiq.bittorrent.format.{TorrentMetadata, TorrentPiece}
import org.apache.commons.io.IOUtils
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class TrackerTest extends FlatSpec with Matchers with BeforeAndAfterAll {
  val actorSystem = ActorSystem("torrent-tracker-test")
  val tracker = actorSystem.actorOf(Props[HttpTracker])

  override protected def afterAll(): Unit = {
    actorSystem.terminate()
    super.afterAll()
  }

  implicit val timeout = Timeout(10 minutes)
  val torrent = TorrentMetadata(ByteString(IOUtils.toByteArray(getClass.getResourceAsStream("ubuntu-15.10-desktop-amd64.iso.torrent")))).get
  val id = ByteString(Array.fill(20)('V'.toByte))
  val result = {
    val response = tracker ? TrackerRequest(torrent.announce, torrent.infoHash, id, 8901, 0, 0, torrent.size)
    Await.result(response, Duration.Inf).asInstanceOf[TrackerResponse]
  }

  "Torrent tracker" should "provide peers" in {
    result.interval shouldBe 1800
    result.complete should be > result.incomplete
    result.peers should not be empty
  }

  "Torrent pieces" should "be downloaded" in {
    val loader = actorSystem.actorOf(Props(classOf[PeerDispatcher], torrent))
    result.peers.foreach(p ⇒ loader ! ConnectPeer(p.address, PeerData(null, id, torrent.infoHash)))
    val piece = {
      val response = loader ? PieceDownloadRequest(0, TorrentPiece.sequence(torrent.files).head)
      Await.result(response, Duration.Inf).asInstanceOf[DownloadedPiece]
    }
    piece.data.length shouldBe torrent.files.pieceLength
  }
}