package com.witt3rd

import zio.*

object HelloWorld extends App {
  import zio.console.*

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    putStrLn("Hello, world!").exitCode
}

object PrintSequenceZip extends App {
  import zio.console.*

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    putStrLn("Hello") *> putStrLn("world!") *> ZIO.succeed(ExitCode.success)
}

object PrintSequenceFor extends App {
  import zio.console.*

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    for {
      _ <- putStrLn("Hello").exitCode
      _ <- putStrLn("world!").exitCode
    } yield ExitCode.success
}

object ErrorRecoveryOrElse extends App {
  import zio.console.*

  val failed =
    putStrLn("About to fail...") *>
      ZIO.fail("Uh oh!") *>
      putStrLn("This will NEVER be printed!")

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (failed as ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object ErrorRecoveryFold extends App {
  import zio.console.*

  val failed =
    putStrLn("About to fail...") *>
      ZIO.fail("Uh oh!") *>
      putStrLn("This will NEVER be printed!")

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    failed.fold(_ => ExitCode.success, _ => ExitCode.failure)
}

object ErrorRecoveryCause extends App {
  import zio.console.*

  val failed =
    putStrLn("About to fail...") *>
      ZIO.fail("Uh oh!") *>
      putStrLn("This will NEVER be printed!")

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (failed as ExitCode.success).catchAllCause(cause =>
      putStrLn(s"${cause.prettyPrint}")
    ) as ExitCode.failure
}

object Looping extends App {
  import zio.console.*

  def repeat[R, E, A](n: Int)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    if (n <= 1) effect
    else effect *> repeat(n - 1)(effect)

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    repeat(100)(
      putStrLn("All work and no play makes Jack a dull boy")
    ).exitCode
}

object PromptName extends App {
  import zio.console.*

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      _    <- putStrLn("What is your name?")
      name <- getStrLn
      _    <- putStrLn(s"Hello ${name}!")
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object NumberGuesser extends App {
  import zio.console.*
  import zio.random.*

  def analyzeAnswer(random: Int, guess: String) =
    if (random.toString == guess.trim) putStrLn("You guessed correctly!")
    else putStrLn(s"You did not guess correctly.  The answer was ${random}")

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      random <- nextIntBounded(3)
      _      <- putStrLn("Please guess a number from 0 to 3: ")
      guess  <- getStrLn
      _      <- analyzeAnswer(random, guess)
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object AlarmAppImproved extends App {
  import zio.console.*
  import zio.duration.*
  import java.io.IOException
  import java.util.concurrent.TimeUnit

  def toDouble(s: String): Either[NumberFormatException, Double] =
    try Right(s.toDouble)
    catch { case e: NumberFormatException => Left(e) }

  lazy val getAlarmDuration: ZIO[Console, IOException, Duration] = {
    def parseDuration(input: String): Either[NumberFormatException, Duration] =
      toDouble(input).map(double => Duration((double * 1000.0).toLong, TimeUnit.MILLISECONDS))

    val fallback = putStrLn("You didn't enter the number of seconds!") *> getAlarmDuration

    for {
      _        <- putStrLn("Please enter the number of seconds to sleep: ")
      input    <- getStrLn
      duration <- ZIO.fromEither(parseDuration(input)) orElse fallback
    } yield duration
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      duration <- getAlarmDuration
      fiber    <- (putStr(".") *> ZIO.sleep(1.second)).forever.fork
      _        <- ZIO.sleep(duration) *> putStrLn("Time to wake up!!!") *> fiber.interrupt
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object ComputePi extends App {
  import zio.console.*
  import zio.random.*
  import zio.clock.*
  import zio.duration.*
  import zio.stm.*

  final case class PiState(inside: Long, total: Long)

  def estimatePi(inside: Long, total: Long): Double = (inside.toDouble / total.toDouble) * 4.0

  def insideCircle(x: Double, y: Double): Boolean = Math.sqrt(x * x + y * y) <= 1.0

  val randomPoint: ZIO[Random, Nothing, (Double, Double)] = nextDouble zip nextDouble

  def updateOnce(ref: Ref[PiState]): ZIO[Random, Nothing, Unit] =
    for {
      tuple <- randomPoint
      (x, y) = tuple
      inside = if (insideCircle(x, y)) 1 else 0
      _ <- ref.update(state => PiState(state.inside + inside, state.total + 1))
    } yield ()

  def printEstimate(ref: Ref[PiState]): ZIO[Console, Nothing, Unit] =
    for {
      state <- ref.get
      _     <- putStrLn(s"${estimatePi(state.inside, state.total)}")
    } yield ()

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] =
    (for {
      ref <- Ref.make(PiState(0L, 0L))
      worker  = updateOnce(ref).forever
      workers = List.fill(40)(worker)
      fiber1 <- ZIO.forkAll(workers)
      fiber2 <- (printEstimate(ref) *> ZIO.sleep(1.second)).forever.fork
      _      <- putStrLn("Enter any key to terminate...")
      _      <- getStrLn *> (fiber1 zip fiber2).interrupt
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
}

object StmDiningPhilosophers extends App {
  import zio.console.*
  import zio.stm.*

  final case class Fork(number: Int)

  final case class Placement(left: TRef[Option[Fork]], right: TRef[Option[Fork]])

  final case class RoundTable(seats: Vector[Placement])

  def takeForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]]): STM[Nothing, (Fork, Fork)] =
    // 1) Long form
    // for {
    //   leftOption <- left.get
    //   leftFork <- leftOption match {
    //     case None       => STM.retry
    //     case Some(fork) => STM.succeed(fork)
    //   }
    //   rightOption <- right.get
    //   rightFork <- rightOption match {
    //     case None       => STM.retry
    //     case Some(fork) => STM.succeed(fork)
    //   }
    // } yield (leftFork, rightFork)
    //
    // 2) Using collect
    // for {
    //   leftFork  <- left.get.collect { case Some(fork) => fork }
    //   rightFork <- right.get.collect { case Some(fork) => fork }
    // } yield (leftFork, rightFork)
    //
    // 3) Using zip
    left.get.collect { case Some(fork) => fork } zip right.get.collect { case Some(fork) => fork }

  def putForks(left: TRef[Option[Fork]], right: TRef[Option[Fork]])(
      tuple: (Fork, Fork)
  ): STM[Nothing, Unit] = {
    val (leftFork, rightFork) = tuple

    for {
      _ <- right.set(Some(rightFork))
      _ <- left.set(Some(leftFork))
    } yield ()
  }

  def setupTable(size: Int): ZIO[Any, Nothing, RoundTable] = {
    def makeFork(i: Int) = TRef.make[Option[Fork]](Some(Fork(i)))

    (for {
      allForks0 <- STM.foreach(0 to size) { i => makeFork(i) }
      allForks   = allForks0 ++ List(allForks0(0))
      placements = (allForks zip allForks.drop(1)).map { case (l, r) => Placement(l, r) }
    } yield RoundTable(placements.toVector)).commit
  }

  def eat(philosopher: Int, roundTable: RoundTable): ZIO[Console, Nothing, Unit] = {
    val placement = roundTable.seats(philosopher)

    val left  = placement.left
    val right = placement.right

    for {
      forks <- takeForks(left, right).commit
      _     <- putStrLn(s"Philosopher ${philosopher} eating...")
      _     <- putForks(left, right)(forks).commit
      _     <- putStrLn(s"Philosopher ${philosopher} is done eating")
    } yield ()
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val count = 10

    def eaters(table: RoundTable): Iterable[ZIO[Console, Nothing, Unit]] =
      (0 to count).map { index => eat(index, table) }

    for {
      table <- setupTable(count)
      fiber <- ZIO.forkAll(eaters(table))
      _     <- fiber.join
      _     <- putStrLn("All philosophers have eaten!")
    } yield ExitCode.success
  }
}

object Actors extends App {
  import zio.console.*
  import zio.stm.*

  sealed trait Command
  case object ReadTemperature                       extends Command
  final case class AdjustTemperature(value: Double) extends Command

  type TemperatureActor = Command => Task[Double]

  def makeActor(initialTemperature: Double): UIO[TemperatureActor] = {
    type Bundle = (Command, Promise[Nothing, Double])

    for {
      ref   <- Ref.make(initialTemperature)
      queue <- Queue.bounded[Bundle](1000)
      _ <- queue.take
        .flatMap {
          case (ReadTemperature, promise) => ref.get.flatMap(promise.succeed(_))
          case (AdjustTemperature(d), promise) =>
            ref.updateAndGet(_ + d).flatMap(promise.succeed(_))
        }
        .forever
        .fork
    } yield (c: Command) =>
      Promise.make[Nothing, Double].flatMap(p => queue.offer(c -> p) *> p.await)
  }

  def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    val temperatures = (0 to 100).map(_.toDouble)

    (for {
      actor <- makeActor(0)
      _     <- ZIO.foreachPar(temperatures) { temp => actor(AdjustTemperature(temp)) }
      temp  <- actor(ReadTemperature)
      _     <- putStrLn(s"Final temperature is ${temp}")
    } yield ExitCode.success) orElse ZIO.succeed(ExitCode.failure)
  }
}
