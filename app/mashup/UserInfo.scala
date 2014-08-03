package lila.app
package mashup

import chess.Color
import org.joda.time.Period

import lila.api.Context
import lila.bookmark.BookmarkApi
import lila.forum.PostApi
import lila.game.{ GameRepo, Game, Crosstable, PlayTime }
import lila.relation.RelationApi
import lila.security.Granter
import lila.user.User

case class UserInfo(
    user: User,
    ranks: Map[lila.rating.Perf.Key, Int],
    nbPlaying: Int,
    crosstable: Option[Crosstable],
    nbBookmark: Int,
    ratingChart: Option[String],
    nbFollowing: Int,
    nbFollowers: Int,
    nbBlockers: Option[Int],
    nbPosts: Int,
    playTime: User.PlayTime,
    donated: Int) {

  def nbRated = user.count.rated

  def nbWithMe = crosstable ?? (_.nbGames)

  def percentRated: Int = math.round(nbRated / user.count.game.toFloat * 100)
}

object UserInfo {

  def apply(
    bookmarkApi: BookmarkApi,
    relationApi: RelationApi,
    gameCached: lila.game.Cached,
    crosstableApi: lila.game.CrosstableApi,
    postApi: PostApi,
    getRatingChart: User => Fu[Option[String]],
    getRanks: String => Fu[Map[String, Int]],
    getDonated: String => Fu[Int])(user: User, ctx: Context): Fu[UserInfo] =
    getRanks(user.id) zip
      ((ctx is user) ?? { gameCached nbPlaying user.id map (_.some) }) zip
      (ctx.me.filter(user!=) ?? { me => crosstableApi(me.id, user.id) }) zip
      getRatingChart(user) zip
      relationApi.nbFollowing(user.id) zip
      relationApi.nbFollowers(user.id) zip
      (ctx.me ?? Granter(_.UserSpy) ?? { relationApi.nbBlockers(user.id) map (_.some) }) zip
      postApi.nbByUser(user.id) zip
      getDonated(user.id) zip
      PlayTime(user) map {
        case (((((((((ranks, nbPlaying), crosstable), ratingChart), nbFollowing), nbFollowers), nbBlockers), nbPosts), donated), playTime) => new UserInfo(
          user = user,
          ranks = ranks,
          nbPlaying = ~nbPlaying,
          crosstable = crosstable,
          nbBookmark = bookmarkApi countByUser user,
          ratingChart = ratingChart,
          nbFollowing = nbFollowing,
          nbFollowers = nbFollowers,
          nbBlockers = nbBlockers,
          nbPosts = nbPosts,
          playTime = playTime,
          donated = donated)
      }
}
