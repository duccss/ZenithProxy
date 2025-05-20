package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.util.config.Config;

import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;

public class ChatSchemaCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("chatSchema")
            .category(CommandCategory.MANAGE)
            .description("""
                Configure how ZenithProxy parses chat messages.
                
                Includes public chats and whispers.
                
                Schemas have the following special tokens:
                * $s -> Chat sender, player name
                * $r -> Chat receiver, player name
                * $m -> Chat message, the actual message content
                * $w -> Wildcard string, can be used to match on any string
                
                Example 2b2t chat schema:
                * public chat: `<$s> $m`
                * whisper outbound: `$s whispers: $m`
                * whisper inbound: `to $r: $m`
                
                You can configure different schemas for different servers based on the server address.
                """)
            .usageLines(
                "add <serverAddress>",
                "remove <serverAddress>",
                "set <publicChat/whisperInbound/whisperOutbound> <serverAddress> <schema>",
                "preset <serverAddress> <2b2t/essentials>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("chatSchema")
            .then(literal("add").then(argument("serverAddress", wordWithChars()).executes(c -> {
                var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, CONFIG.client.chatSchemas.defaultSchema);
                c.getSource().getEmbed()
                    .title("Server Added");
            })))
            .then(literal("remove").then(argument("serverAddress", wordWithChars()).executes(c -> {
                var serverAddress = getString(c, "serverAddress");
                CONFIG.client.chatSchemas.serverSchemas.remove(serverAddress);
                c.getSource().getEmbed()
                    .title("Server Removed");
            })))
            .then(literal("set")
                .then(literal("publicChat").then(argument("serverAddress", wordWithChars()).then(argument("schema", greedyString()).executes(c -> {
                    var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                    var schemaArg = getString(c, "schema");
                    var originalSchema = CONFIG.client.chatSchemas.serverSchemas.getOrDefault(serverAddress, CONFIG.client.chatSchemas.defaultSchema);
                    var newSchema = new Config.Client.ChatSchemas.ChatSchema(schemaArg, originalSchema.whisperInbound, originalSchema.whisperOutbound);
                    CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, newSchema);
                    c.getSource().getEmbed()
                        .title("Public Chat Schema Set");
                }))))
                .then(literal("whisperInbound").then(argument("serverAddress", wordWithChars()).then(argument("schema", greedyString()).executes(c -> {
                    var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                    var schemaArg = getString(c, "schema");
                    var originalSchema = CONFIG.client.chatSchemas.serverSchemas.getOrDefault(serverAddress, CONFIG.client.chatSchemas.defaultSchema);
                    var newSchema = new Config.Client.ChatSchemas.ChatSchema(originalSchema.publicChat, schemaArg, originalSchema.whisperOutbound);
                    CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, newSchema);
                    c.getSource().getEmbed()
                        .title("Inbound Whisper Schema Set");
                }))))
                .then(literal("whisperOutbound").then(argument("serverAddress", wordWithChars()).then(argument("schema", greedyString()).executes(c -> {
                    var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                    var schemaArg = getString(c, "schema");
                    var originalSchema = CONFIG.client.chatSchemas.serverSchemas.getOrDefault(serverAddress, CONFIG.client.chatSchemas.defaultSchema);
                    var newSchema = new Config.Client.ChatSchemas.ChatSchema(originalSchema.publicChat, originalSchema.whisperInbound, schemaArg);
                    CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, newSchema);
                    c.getSource().getEmbed()
                        .title("Outbound Whisper Schema Set");
                })))))
            .then(literal("preset").then(argument("serverAddress", wordWithChars())
                .then(literal("2b2t").executes(c -> {
                    var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                    var schema2b2t = new Config.Client.ChatSchemas.ChatSchema(
                        "<$s> $m",
                        "$s whispers: $m",
                        "to $r: $m"
                    );
                    CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, schema2b2t);
                    c.getSource().getEmbed()
                        .title("2b2t Schema Set");
                }))
                .then(literal("essentials").executes(c -> {
                    var serverAddress = getString(c, "serverAddress").toLowerCase().trim();
                    var schemaEssentials = new Config.Client.ChatSchemas.ChatSchema(
                        "<$s> $m",
                        "[$s -> me] $m",
                        "[me -> $r] $m"
                    );
                    CONFIG.client.chatSchemas.serverSchemas.put(serverAddress, schemaEssentials);
                    c.getSource().getEmbed()
                        .title("Essentials Schema Set");
                }))));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .description(getSchemaList())
            .primaryColor();
    }

    private String getSchemaList() {
        StringBuilder list = new StringBuilder();
        list.append("**Default Schema**\n");
        list.append(printSchema(CONFIG.client.chatSchemas.defaultSchema));
        if (!CONFIG.client.chatSchemas.serverSchemas.isEmpty()) {
            for (var serverSchemaEntries : CONFIG.client.chatSchemas.serverSchemas.entrySet()) {
                var serverAddress = serverSchemaEntries.getKey();
                var schema = serverSchemaEntries.getValue();
                list.append("**Server: %s**\n".formatted(serverAddress));
                list.append(printSchema(schema));
            }
        }
        return list.toString();
    }

    private String printSchema(Config.Client.ChatSchemas.ChatSchema schema) {
        return """
              * Public Chat: `%s`
              * Whisper Inbound: `%s`
              * Whisper Outbound: `%s`
            """.formatted(
            schema.publicChat,
            schema.whisperInbound,
            schema.whisperOutbound
        );
    }
}
