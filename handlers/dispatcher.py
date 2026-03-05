import logging
from pyrogram import Client, filters
from pyrogram.types import Message
from db import Database
from filters import should_forward

logger = logging.getLogger(__name__)


def register(app: Client, get_db):

    @app.on_message(filters.channel)
    async def on_channel_post(client: Client, message: Message):
        channel = message.chat.username
        if not channel:
            return

        async with get_db() as db:
            subscribers = await db.get_active_subscribers(channel)

        if not subscribers:
            return

        text = message.text or message.caption or ""
        for sub in subscribers:
            if should_forward(text=text, mode=sub["mode"], keywords=sub["keywords"]):
                try:
                    await client.forward_messages(
                        chat_id=sub["user_id"],
                        from_chat_id=message.chat.id,
                        message_ids=message.id,
                    )
                except Exception as e:
                    err_name = type(e).__name__
                    if "UserIsBlocked" in err_name or "InputUserDeactivated" in err_name:
                        logger.warning("User %s blocked bot, deactivating", sub["user_id"])
                        async with get_db() as db:
                            await db.set_user_active(user_id=sub["user_id"], active=False)
                    else:
                        logger.error("Forward error for user %s: %s", sub["user_id"], e)
