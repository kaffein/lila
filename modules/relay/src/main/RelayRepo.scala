package lila.relay

import org.joda.time.DateTime
import reactivemongo.bson._
import reactivemongo.core.commands._

import BSONHandlers._
import lila.db.BSON._
import lila.db.Implicits._

final class RelayRepo(coll: Coll) {

  private def selectId(id: String) = BSONDocument("_id" -> id)
  private def selectName(name: String) = BSONDocument("name" -> name)
  private val selectStarted = BSONDocument("status" -> Relay.Status.Started.id)
  private val selectEnabled = BSONDocument("enabled" -> true)
  private val selectEnabledStarted = selectEnabled ++ selectStarted
  private val selectRecent = BSONDocument("date" -> BSONDocument("$gt" -> DateTime.now.minusWeeks(2)))
  private[relay] val selectNonEmpty = BSONDocument("games.0.id" -> BSONDocument("$exists" -> true))
  private[relay] val selectPresentable = selectEnabled ++ selectNonEmpty
  private[relay] val sortRecent = BSONDocument("date" -> -1)
  private[relay] val sortElo = BSONDocument("elo" -> -1)

  def byId(id: String): Fu[Option[Relay]] = coll.find(selectId(id)).one[Relay]

  def recentByName(name: String): Fu[Option[Relay]] =
    coll.find(selectEnabled ++ selectName(name) ++ selectRecent).one[Relay]

  def started: Fu[List[Relay]] = coll.find(selectStarted).cursor[Relay]().collect[List]()

  def startedTopRated(nb: Int): Fu[List[Relay]] =
    coll.find(selectEnabledStarted).sort(sortElo).cursor[Relay]().collect[List](nb)

  def recent(nb: Int): Fu[List[Relay]] =
    coll.find(BSONDocument()).sort(sortRecent).cursor[Relay]().collect[List](nb)

  def exists(id: String): Fu[Boolean] = coll.count(selectId(id).some) map (0 !=)

  def withGamesButNoElo: Fu[List[Relay]] =
    coll.find(selectStarted ++ BSONDocument(
      "elo" -> BSONDocument("$exists" -> false),
      "games.0" -> BSONDocument("$exists" -> true)
    )).cursor[Relay]().collect[List]()

  def setElo(id: String, elo: Int) = coll.update(
    selectId(id),
    BSONDocument("$set" -> BSONDocument("elo" -> elo))
  ).void

  def upsert(ficsId: Int, name: String, status: Relay.Status) = recentByName(name) flatMap {
    case None        => coll insert Relay.make(ficsId, name, status)
    case Some(relay) => coll.update(selectId(relay.id), relay.copy(ficsId = ficsId, status = status))
  } void

  def finish(relay: Relay) = coll.update(
    selectName(relay.name),
    BSONDocument("$set" -> BSONDocument("status" -> Relay.Status.Finished.id))
  ).void

  def setGames(relay: Relay): Funit = coll.update(
    selectId(relay.id),
    BSONDocument("$set" -> BSONDocument("games" -> relay.games))
  ).void

  def gameByFicsId(id: String, ficsId: Int): Fu[Option[Relay.Game]] =
    byId(id) map { _ flatMap (_ gameByFicsId ficsId) }

  def endGameByFicsId(id: String, ficsId: Int): Funit = coll.update(
    selectId(id) ++ BSONDocument("games.ficsId" -> ficsId),
    BSONDocument("$set" -> BSONDocument("games.$.end" -> true))
  ).void
}
