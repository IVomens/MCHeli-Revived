package com.ivomens.mcheli.networking;

/** A message a client sends to the server. Its content is untrusted: handlers check what it names. */
public interface ServerboundMessage extends Message {
}
