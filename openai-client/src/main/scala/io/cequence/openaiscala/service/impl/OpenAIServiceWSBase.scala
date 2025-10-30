package io.cequence.openaiscala.service.impl

import io.cequence.openaiscala.service.OpenAIServiceConsts
import io.cequence.wsclient.service.WSClientWithEngineTypes.WSClientWithEngine

trait OpenAIServiceWSBase extends WSClientWithEngine with OpenAIServiceConsts {

  override protected type PEP = EndPoint
  override protected type PT = Param
}
