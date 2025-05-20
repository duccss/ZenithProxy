package com.zenith.feature.chatschema;

import com.zenith.util.config.Config;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;

@NullMarked
public class ChatSchemaParser {

    private static final String senderToken = "$s";
    private static final String receiverToken = "$r";
    private static final String messageToken = "$m";
    private static final String wildcardStringToken = "$w";

    public record ChatParseResult(
        ChatType type,
        @Nullable PlayerListEntry sender,
        @Nullable PlayerListEntry receiver,
        @Nullable String messageContent
    ) {}

    public static @Nullable ChatParseResult parse(String input) {
        return parse(input, getSchema());
    }

    public static @Nullable ChatParseResult parse(String input, Config.Client.ChatSchemas.ChatSchema schema) {
        var outboundWhisperParse = tryParseOutboundWhisper(input, schema.whisperOutbound);
        if (outboundWhisperParse != null) {
            return outboundWhisperParse;
        }
        var inboundWhisperParse = tryParseInboundWhisper(input, schema.whisperInbound);
        if (inboundWhisperParse != null) {
            return inboundWhisperParse;
        }
        var publicChatParse = tryParsePublicChat(input, schema.publicChat);
        if (publicChatParse != null) {
            return publicChatParse;
        }
        return null;
    }

    private static Config.Client.ChatSchemas.ChatSchema getSchema() {
        return CONFIG.client.chatSchemas.serverSchemas.getOrDefault(
            CONFIG.client.server.address,
            CONFIG.client.chatSchemas.defaultSchema
        );
    }

    private static @Nullable ChatParseResult tryParsePublicChat(String rawInput, String publicChatSchema) {
        return tryParseChat(ChatType.PUBLIC_CHAT, rawInput, publicChatSchema);
    }

    public static @Nullable ChatParseResult tryParseOutboundWhisper(String rawInput, String outboundWhisperSchema) {
        return tryParseChat(ChatType.WHISPER_OUTBOUND, rawInput, outboundWhisperSchema);
    }

    public static @Nullable ChatParseResult tryParseInboundWhisper(String rawInput, String inboundWhisperSchema) {
        return tryParseChat(ChatType.WHISPER_INBOUND, rawInput, inboundWhisperSchema);
    }

    private static @Nullable ChatParseResult tryParseChat(ChatType type, String rawInput, String inputSchema) {
        try {
            return tryParseChat0(type, rawInput, inputSchema);
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable ChatParseResult tryParseChat0(ChatType type, String rawInput, String inputSchema) {
        var schema = Arrays.asList(inputSchema.split(" "));
        var input = Arrays.asList(rawInput.split(" "));
        PlayerListEntry sender = null;
        PlayerListEntry receiver = null;
        String messageContent = null;

        for (int i = 0; i < schema.size(); i++) {
            var schemaWord = schema.get(i);
            var inputWord = input.get(i);
            if (schemaWord.contains("$")) {
                if (schemaWord.length() == 2) {
                    // single token
                    if (schemaWord.equals(senderToken)) {
                        var senderEntryOptional = CACHE.getTabListCache().getFromName(inputWord);
                        if (senderEntryOptional.isEmpty()) {
                            return null;
                        } else {
                            sender = senderEntryOptional.get();
                        }
                    } else if (schemaWord.equals(receiverToken)) {
                        var receiverEntryOptional = CACHE.getTabListCache().getFromName(inputWord);
                        if (receiverEntryOptional.isEmpty()) {
                            return null;
                        } else {
                            receiver = receiverEntryOptional.get();
                        }
                    } else if (schemaWord.equals(messageToken)) {
                        // match rest of the message
                        // as long as we don't have any following schema tokens
                        if (i != schema.size() - 1) {
                            // we have more schema tokens
                            // todo: handle this
                            return null;
                        } else {
                            messageContent = String.join(" ", input.subList(i, input.size()));
                        }
                    } else if (schemaWord.equals(wildcardStringToken)) {
                        // match until the next schema token, including spaces and multiple words
                        // todo:
                        continue;
                    }
                } else {
                    // single token with extra characters
                    var tokenStartIndex = schemaWord.indexOf("$");
                    // all tokens are 2 characters long
                    var tokenEndIndex = schemaWord.indexOf("$") + 2;
                    var token = schemaWord.substring(tokenStartIndex, tokenEndIndex);
                    String leadingText = schemaWord.substring(0, tokenStartIndex);
                    String trailingText = schemaWord.substring(tokenEndIndex);
                    // check if the leading text matches
                    if (!inputWord.startsWith(leadingText)) {
                        return null;
                    }
                    // check if the trailing text matches
                    if (!inputWord.endsWith(trailingText)) {
                        return null;
                    }
                    var inputWordCleaned = inputWord.substring(leadingText.length(), inputWord.length() - trailingText.length());
                    // check if the token matches
                    if (token.equals(senderToken)) {
                        var senderEntryOptional = CACHE.getTabListCache().getFromName(inputWordCleaned);
                        if (senderEntryOptional.isEmpty()) {
                            return null;
                        } else {
                            sender = senderEntryOptional.get();
                        }
                    } else if (token.equals(receiverToken)) {
                        var receiverEntryOptional = CACHE.getTabListCache().getFromName(inputWordCleaned);
                        if (receiverEntryOptional.isEmpty()) {
                            return null;
                        } else {
                            receiver = receiverEntryOptional.get();
                        }
                    } else if (token.equals(messageToken)) {
                        // match rest of the message
                        // as long as we don't have any following schema tokens
                        if (i != schema.size() - 1) {
                            // we have more schema tokens
                            return null;
                        } else {
                            // todo: fix this
                            messageContent = String.join(" ", input.subList(i, input.size()));
                        }
                    } else if (token.equals(wildcardStringToken)) {
                        // match until end of token
                        continue;
                    }
                }
            } else {
                // no token, just a word
                if (!inputWord.equals(schemaWord)) {
                    return null;
                }
            }
        }
        switch (type) {
            case PUBLIC_CHAT -> {
                if (sender == null) return null;
                if (receiver != null) return null;
                if (messageContent == null) return null;
            }
            case WHISPER_OUTBOUND -> {
                if (sender == null) sender = getSelfEntry();
                else if (sender != getSelfEntry()) return null;
                if (receiver == null) return null;
                if (messageContent == null) return null;
            }
            case WHISPER_INBOUND -> {
                if (sender == null) return null;
                if (receiver == null) receiver = getSelfEntry();
                else if (receiver != getSelfEntry()) return null;
                if (messageContent == null) return null;
            }
        }
        return new ChatParseResult(type, sender, receiver, messageContent);
    }

    private static @Nullable PlayerListEntry getSelfEntry() {
        var selfProfile = CACHE.getProfileCache().getProfile();
        if (selfProfile == null) return null;
        return CACHE.getTabListCache().get(selfProfile.getId()).orElse(null);
    }
}
