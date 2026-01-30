package de.innfactory.smithy4play.routing.internal

import play.api.mvc.RawBuffer
import smithy4s.Blob

/**
 * Utility object for creating Blobs lazily from Play's RawBuffer.
 * 
 * Since Smithy4s Blob is sealed, we can't extend it directly.
 * Instead, this provides factory methods that defer materialization
 * until the Blob is actually needed.
 */
object LazyBlob {
  
  /**
   * Create a Blob from a RawBuffer with lazy materialization.
   * 
   * This avoids unnecessary memory copies for requests where the body
   * is not needed (e.g., validation failures, GET requests).
   * 
   * Note: The actual bytes are read when this method is called,
   * but the method itself should be called lazily (via lazy val).
   */
  def apply(rawBuffer: RawBuffer): Blob = {
    if (rawBuffer.size == 0) {
      Blob.empty
    } else {
      // Read bytes from RawBuffer and create Blob
      rawBuffer.asBytes() match {
        case Some(byteString) => Blob(byteString.toArrayUnsafe())
        case None             => Blob.empty
      }
    }
  }
  
  /**
   * Create a Blob from an optional RawBuffer.
   */
  def fromOption(rawBuffer: Option[RawBuffer]): Blob = {
    rawBuffer.map(apply).getOrElse(Blob.empty)
  }
}

/**
 * Wrapper that provides lazy Blob creation.
 * 
 * Use this when you want to defer body parsing until it's actually needed.
 * The Blob is created on first access and cached for subsequent accesses.
 * 
 * Example:
 * {{{
 * val lazyBody = LazyBlobHolder(rawBuffer)
 * // ... later, only if body is needed:
 * val blob = lazyBody.blob
 * }}}
 */
final class LazyBlobHolder(rawBuffer: RawBuffer) {
  
  /**
   * Lazily materialized Blob.
   * The RawBuffer is only read when this is first accessed.
   */
  lazy val blob: Blob = LazyBlob(rawBuffer)
  
  /**
   * Size of the body without materializing it.
   */
  def size: Long = rawBuffer.size
  
  /**
   * Check if body is empty without materializing it.
   */
  def isEmpty: Boolean = rawBuffer.size == 0
}

object LazyBlobHolder {
  def apply(rawBuffer: RawBuffer): LazyBlobHolder = new LazyBlobHolder(rawBuffer)
}
