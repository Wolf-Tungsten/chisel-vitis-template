package vitisrtlkernel.util

import chisel3._
import chisel3.Printable

trait DebugLog {
  object LogLevel {
    val DEBUG = 3
    val INFO = 2
    val ERROR = 1
    val QUIET = 0
  }
  var logLevel = LogLevel.QUIET
  def debugLog(p:Printable) = {
    if(logLevel >= LogLevel.DEBUG){
      printf(p)
    }
  }
  def infoLog(p:Printable) = {
    if(logLevel >= LogLevel.INFO){
      printf(p)
    }
  }
  def errorLog(p:Printable) = {
    if(logLevel >= LogLevel.ERROR){
      printf(p)
    }
  }
}