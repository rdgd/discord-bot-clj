build:
	clj -Spom
	clj -A:jar discord-bot.jar

deploy:
	clj -X:deploy
