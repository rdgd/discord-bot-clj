.PHONY: deploy

deploy: test
	clj -Spom
	mvn deploy
