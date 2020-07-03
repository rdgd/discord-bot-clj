build:
	clj -Spom
	clj -A:jar discord-bot.clj

deploy:
	clj -A:deploy discord-bot.clj
