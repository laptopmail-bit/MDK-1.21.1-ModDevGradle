package com.example.examplemod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "examplemod";

    public ExampleMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("invite")
                .executes(ctx -> run(ctx.getSource(), 25565))
                .then(Commands.argument("port", IntegerArgumentType.integer(1024, 65535))
                    .executes(ctx -> run(ctx.getSource(),
                        IntegerArgumentType.getInteger(ctx, "port"))))
        );
    }

    private int run(CommandSourceStack src, int port) {
        MinecraftServer server = src.getServer();

        if (!(server instanceof IntegratedServer integrated)) {
            src.sendFailure(Component.literal("Команда работает только в одиночном мире."));
            return 0;
        }

        if (!integrated.isPublished()) {
            boolean ok = integrated.publishServer(integrated.getDefaultGameType(), false, port);
            if (!ok) {
                src.sendFailure(Component.literal("Не удалось открыть порт " + port));
                return 0;
            }
        }

        final int actualPort = integrated.getPort();
        src.sendSuccess(() -> Component.literal("Мир открыт на порту " + actualPort
            + ". Получаю внешний IP..."), false);

        CompletableFuture.supplyAsync(ExampleMod::fetchPublicIp)
            .thenAccept(ip -> server.execute(() -> {
                if (ip == null) {
                    src.sendFailure(Component.literal("Не удалось узнать внешний IP."));
                    return;
                }
                String address = ip + ":" + actualPort;
                Component msg = Component.literal("Адрес для друга: " + address + " (нажми, чтобы скопировать)")
                    .withStyle(s -> s
                        .withColor(0x55FF55)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, address)));
                src.sendSuccess(() -> msg, false);
            }));

        return 1;
    }

    private static String fetchPublicIp() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.ipify.org")).build();
            return client.send(req, HttpResponse.BodyHandlers.ofString()).body().trim();
        } catch (Exception e) {
            return null;
        }
    }
}
