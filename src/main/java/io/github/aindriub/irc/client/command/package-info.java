/**
 * The outbound protocol messages.
 *
 * <p>Every command validates its arguments, so text taken from chat cannot inject a
 * second IRC message. {@code render()} is the wire form and {@code toString()} the
 * log form; they differ only where a command carries a credential.
 */
package io.github.aindriub.irc.client.command;
