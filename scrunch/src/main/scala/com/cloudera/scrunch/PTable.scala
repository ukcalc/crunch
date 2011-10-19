package com.cloudera.scrunch

import com.cloudera.crunch.{DoFn, Emitter, FilterFn, MapFn}
import com.cloudera.crunch.{GroupingOptions, PTable => JTable, Pair => JPair}
import Conversions._

class PTable[K, V](jtable: JTable[K, V]) extends PCollection[JPair[K, V]](jtable) with JTable[K, V] {

  def filter(f: (K, V) => Boolean): PTable[K, V] = {
    parallelDo(new DSFilterTableFn[K, V](f), getPTableType())
  }

  def map[T: ClassManifest](f: (K, V) => T) = {
    parallelDo(new DSMapTableFn[K, V, T](f), createPType(classManifest[T]))
  }

  def map2[L: ClassManifest, W: ClassManifest](f: (K, V) => (L, W)) = {
    parallelDo(new DSMapTableFn2[K, V, L, W](f), createPTableType(classManifest[L], classManifest[W]))
  }

  def flatMap[T: ClassManifest](f: (K, V) => Traversable[T]) = {
    parallelDo(new DSDoTableFn[K, V, T](f), createPType(classManifest[T]))
  }

  def flatMap2[L: ClassManifest, W: ClassManifest](f: (K, V) => Traversable[(L, W)]) = {
    parallelDo(new DSDoTableFn2[K, V, L, W](f), createPTableType(classManifest[L], classManifest[W]))
  }

  override def union(tables: JTable[K, V]*) = {
    new PTable[K, V](jtable.union(tables.map(baseCheck): _*))
  }

  private def baseCheck(c: JTable[K, V]): JTable[K, V] = c match {
    case x: PTable[K, V] => x.base.asInstanceOf[PTable[K, V]]
    case _ => c
  }

  def ++ (other: JTable[K, V]) = union(other)

  override def groupByKey() = new PGroupedTable(jtable.groupByKey())

  override def groupByKey(partitions: Int) = new PGroupedTable(jtable.groupByKey(partitions))

  override def groupByKey(options: GroupingOptions) = new PGroupedTable(jtable.groupByKey(options))
  
  override def getPTableType() = jtable.getPTableType()

  override def getKeyType() = jtable.getKeyType()

  override def getValueType() = jtable.getValueType()
}

trait SFilterTableFn[K, V] extends FilterFn[JPair[K, V]] with Function2[K, V, Boolean] {
  override def accept(input: JPair[K, V]): Boolean = {
    apply(c2s(input.first()).asInstanceOf[K], c2s(input.second()).asInstanceOf[V]);
  }
}

trait SDoTableFn[K, V, T] extends DoFn[JPair[K, V], T] with Function2[K, V, Traversable[T]] {
  override def process(input: JPair[K, V], emitter: Emitter[T]): Unit = {
    val k = c2s(input.first()).asInstanceOf[K]
    val v = c2s(input.second()).asInstanceOf[V]
    for (v <- apply(k, v)) {
      emitter.emit(s2c(v).asInstanceOf[T])
    }
  }
}

trait SDoTableFn2[K, V, L, W] extends DoFn[JPair[K, V], JPair[L, W]] with Function2[K, V, Traversable[(L, W)]] {
  override def process(input: JPair[K, V], emitter: Emitter[JPair[L, W]]): Unit = {
    val k = c2s(input.first()).asInstanceOf[K]
    val v = c2s(input.second()).asInstanceOf[V]
    for ((f, s) <- apply(k, v)) {
      emitter.emit(JPair.of(s2c(f), s2c(s)).asInstanceOf[JPair[L, W]])
    }
  }
}

trait SMapTableFn[K, V, T] extends MapFn[JPair[K, V], T] with Function2[K, V, T] {
  override def map(input: JPair[K, V]): T = {
    val v = apply(c2s(input.first()).asInstanceOf[K], c2s(input.second()).asInstanceOf[V])
    s2c(v).asInstanceOf[T]
  }
}

trait SMapTableFn2[K, V, L, W] extends MapFn[JPair[K, V], JPair[L, W]] with Function2[K, V, (L, W)] {
  override def map(input: JPair[K, V]): JPair[L, W] = {
    val k = c2s(input.first()).asInstanceOf[K]
    val v = c2s(input.second()).asInstanceOf[V]
    val (f, s) = apply(k, v)
    JPair.of(s2c(f), s2c(s)).asInstanceOf[JPair[L, W]]
  }
}

class DSFilterTableFn[K, V](fn: (K, V) => Boolean) extends SFilterTableFn[K, V] {
  ClosureCleaner.clean(fn)
  override def apply(k: K, v: V) = fn(k, v)  
}

class DSDoTableFn[K, V, T](fn: (K, V) => Traversable[T]) extends SDoTableFn[K, V, T] {
  ClosureCleaner.clean(fn)
  override def apply(k: K, v: V) = fn(k, v)  
}

class DSDoTableFn2[K, V, L, W](fn: (K, V) => Traversable[(L, W)]) extends SDoTableFn2[K, V, L, W] {
  ClosureCleaner.clean(fn)
  override def apply(k: K, v: V) = fn(k, v)  
}

class DSMapTableFn[K, V, T](fn: (K, V) => T) extends SMapTableFn[K, V, T] {
  ClosureCleaner.clean(fn)
  override def apply(k: K, v: V) = fn(k, v)  
}

class DSMapTableFn2[K, V, L, W](fn: (K, V) => (L, W)) extends SMapTableFn2[K, V, L, W] {
  ClosureCleaner.clean(fn)
  override def apply(k: K, v: V) = fn(k, v)
}