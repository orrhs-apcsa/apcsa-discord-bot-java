import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

// example https://github.com/DV8FromTheWorld/JDA/blob/master/src/examples/java/MessageListenerExample.java

public class TextHandler extends ListenerAdapter{
        @Override
        public void onMessageReceived(@NotNull MessageReceivedEvent event)
        {
            JDA jda = event.getJDA();
            long responseNumber = event.getResponseNumber();

            User author = event.getAuthor();
            Message message = event.getMessage();
            MessageChannel channel = event.getChannel();

            String msg = message.getContentDisplay(); // human readable version of the message

            boolean bot = author.isBot(); // check to see if it is the bot or not

            if(event.isFromType(ChannelType.TEXT)){ //if this is sent to a textchannel
                // this is from a guild, we checked with type. If we didn't check it might not be from a guild
                Guild guild = event.getGuild();
                TextChannel textChannel = event.getTextChannel();
                Member member = event.getMember();

                String name;
                if (message.isWebhookMessage())
                {
                    name = author.getName();                //If this is a Webhook message, then there is no Member associated
                }                                           // with the User, thus we default to the author for name.
                else
                {
                    assert member != null;
                    name = member.getEffectiveName();       //This will either use the Member's nickname if they have one,
                }                                           // otherwise it will default to their username. (User#getName())

                System.out.printf("(%s)[%s]<%s>: %s\n", guild.getName(), textChannel.getName(), name, msg);

            }
            else if (event.isFromType(ChannelType.PRIVATE)) //If this message was sent to a PrivateChannel
            {
                //The message was sent in a PrivateChannel.
                //In this example we don't directly use the privateChannel, however, be sure, there are uses for it!
                PrivateChannel privateChannel = event.getPrivateChannel();

                System.out.printf("[PRIV]<%s>: %s\n", author.getName(), msg);
            }
            if (msg.equals("!ping"))
            {
                //This will send a message, "pong!", by constructing a RestAction and "queueing" the action with the Requester.
                // By calling queue(), we send the Request to the Requester which will send it to discord. Using queue() or any
                // of its different forms will handle ratelimiting for you automatically!

                channel.sendMessage("pong!").queue();
            }
            else if (msg.equals("!roll"))
            {
                //In this case, we have an example showing how to use the flatMap operator for a RestAction. The operator
                // will provide you with the object that results after you execute your RestAction. As a note, not all RestActions
                // have object returns and will instead have Void returns. You can still use the flatMap operator to run chain another RestAction!

                Random rand = ThreadLocalRandom.current();
                int roll = rand.nextInt(6) + 1; //This results in 1 - 6 (instead of 0 - 5)
                channel.sendMessage("Your roll: " + roll)
                        .flatMap(
                                (v) -> roll < 3, // This is called a lambda expression. If you don't know what they are or how they work, try google!
                                // Send another message if the roll was bad (less than 3)
                                sentMessage -> channel.sendMessage("The roll for messageId: " + sentMessage.getId() + " wasn't very good... Must be bad luck!\n")
                        )
                        .queue();
            }
            else if (msg.startsWith("!kick"))   //Note, I used "startsWith, not equals.
            {
                //This is an admin command. That means that it requires specific permissions to use it, in this case
                // it needs Permission.KICK_MEMBERS. We will have a check before we attempt to kick members to see
                // if the logged in account actually has the permission, but considering something could change after our
                // check we should also take into account the possibility that we don't have permission anymore, thus Discord
                // response with a permission failure!
                //We will use the error consumer, the second parameter in queue!

                //We only want to deal with message sent in a Guild.
                if (message.isFromType(ChannelType.TEXT))
                {
                    //If no users are provided, we can't kick anyone!
                    if (message.getMentionedUsers().isEmpty())
                    {
                        channel.sendMessage("You must mention 1 or more Users to be kicked!").queue();
                    }
                    else
                    {
                        Guild guild = event.getGuild();
                        Member selfMember = guild.getSelfMember();  //This is the currently logged in account's Member object.
                        // Very similar to JDA#getSelfUser()!

                        //Now, we the the logged in account doesn't have permission to kick members.. well.. we can't kick!
                        if (!selfMember.hasPermission(Permission.KICK_MEMBERS))
                        {
                            channel.sendMessage("Sorry! I don't have permission to kick members in this Guild!").queue();
                            return; //We jump out of the method instead of using cascading if/else
                        }

                        //Loop over all mentioned users, kicking them one at a time. Mwauahahah!
                        List<User> mentionedUsers = message.getMentionedUsers();
                        for (User user : mentionedUsers)
                        {
                            Member member = guild.getMember(user);  //We get the member object for each mentioned user to kick them!

                            //We need to make sure that we can interact with them. Interacting with a Member means you are higher
                            // in the Role hierarchy than they are. Remember, NO ONE is above the Guild's Owner. (Guild#getOwner())
                            if (!selfMember.canInteract(member))
                            {
                                // use the MessageAction to construct the content in StringBuilder syntax using append calls
                                channel.sendMessage("Cannot kick member: ")
                                        .append(member.getEffectiveName())
                                        .append(", they are higher in the hierarchy than I am!")
                                        .queue();
                                continue;   //Continue to the next mentioned user to be kicked.
                            }

                            //Remember, due to the fact that we're using queue we will never have to deal with RateLimits.
                            // JDA will do it all for you so long as you are using queue!
                            guild.kick(member).queue(
                                    success -> channel.sendMessage("Kicked ").append(member.getEffectiveName()).append("! Cya!").queue(),
                                    error ->
                                    {
                                        //The failure consumer provides a throwable. In this case we want to check for a PermissionException.
                                        if (error instanceof PermissionException)
                                        {
                                            PermissionException pe = (PermissionException) error;
                                            Permission missingPermission = pe.getPermission();  //If you want to know exactly what permission is missing, this is how.
                                            //Note: some PermissionExceptions have no permission provided, only an error message!

                                            channel.sendMessage("PermissionError kicking [")
                                                    .append(member.getEffectiveName()).append("]: ")
                                                    .append(error.getMessage()).queue();
                                        }
                                        else
                                        {
                                            channel.sendMessage("Unknown error while kicking [")
                                                    .append(member.getEffectiveName())
                                                    .append("]: <").append(error.getClass().getSimpleName()).append(">: ")
                                                    .append(error.getMessage()).queue();
                                        }
                                    });
                        }
                    }
                }
                else
                {
                    channel.sendMessage("This is a Guild-Only command!").queue();
                }
            }
            else if (msg.equals("!block"))
            {
                //This is an example of how to use the complete() method on RestAction. The complete method acts similarly to how
                // JDABuilder's awaitReady() works, it waits until the request has been sent before continuing execution.
                //Most developers probably wont need this and can just use queue. If you use complete, JDA will still handle ratelimit
                // control, however if shouldQueue is false it won't queue the Request to be sent after the ratelimit retry after time is past. It
                // will instead fire a RateLimitException!
                //One of the major advantages of complete() is that it returns the object that queue's success consumer would have,
                // but it does it in the same execution context as when the request was made. This may be important for most developers,
                // but, honestly, queue is most likely what developers will want to use as it is faster.

                try
                {
                    //Note the fact that complete returns the Message object!
                    //The complete() overload queues the Message for execution and will return when the message was sent
                    //It does handle rate limits automatically
                    Message sentMessage = channel.sendMessage("I blocked and will return the message!").complete();
                    //This should only be used if you are expecting to handle rate limits yourself
                    //The completion will not succeed if a rate limit is breached and throw a RateLimitException
                    Message sentRatelimitMessage = channel.sendMessage("I expect rate limitation and know how to handle it!").complete(false);

                    System.out.println("Sent a message using blocking! Luckly I didn't get Ratelimited... MessageId: " + sentMessage.getId());
                }
                catch (RateLimitedException e)
                {
                    System.out.println("Whoops! Got ratelimited when attempting to use a .complete() on a RestAction! RetryAfter: " + e.getRetryAfter());
                }
                //Note that RateLimitException is the only checked-exception thrown by .complete()
                catch (RuntimeException e)
                {
                    System.out.println("Unfortunately something went wrong when we tried to send the Message and .complete() threw an Exception.");
                    e.printStackTrace();
                }
            }
        }
}