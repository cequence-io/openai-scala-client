package io.cequence.openaiscala.examples

import scala.concurrent.Future
object ListFiles extends Example {

  //  FileInfo(file-bRFkk72miUWa48tDrE9b2lnL,329,Tue May 07 16:22:54 CEST 2024,None,/var/folders/z6/ylt_9rgd3gq4698960rz1ng40000gn/T/chat_completions12726724005947221132.jsonl,batch,processed,None,None)
  //  FileInfo(file-mjdvW9DTeWDXO2g6sks1kvuQ,331,Tue May 07 15:20:47 CEST 2024,None,/var/folders/z6/ylt_9rgd3gq4698960rz1ng40000gn/T/chat_completions5280933836769757363.json,batch,processed,None,None)

  override protected def run: Future[Unit] =
    service.listFiles.map(_.foreach(println))
}
