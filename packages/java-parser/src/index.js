"use strict";
const JavaLexer = require("./lexer");
const JavaParser = require("./parser");
const { tokenMatcher } = require("chevrotain");
const { tokens: t } = require("./tokens");

// const startTime = new Date().getTime();
const parser = new JavaParser();
const BaseJavaCstVisitor = parser.getBaseCstVisitorConstructor();
const BaseJavaCstVisitorWithDefaults = parser.getBaseCstVisitorConstructorWithDefaults();

// const endTime = new Date().getTime();
// const totalTime = endTime - startTime;
// console.log("parse start time (ms): " + totalTime);

function parse(inputText, entryPoint = "compilationUnit") {
  // Lex
  const lexResult = JavaLexer.tokenize(inputText);

  if (lexResult.errors.length > 0) {
    const firstError = lexResult.errors[0];
    throw Error(
      "Sad sad panda, lexing errors detected in line: " +
        firstError.line +
        ", column: " +
        firstError.column +
        "!\n" +
        firstError.message
    );
  }

  const tokens = lexResult.tokens;
  const newTokens = [];
  const headcomments = [];
  let i = 0;
  while (tokenMatcher(tokens[i].tokenType, t.Comment) && i < tokens.length) {
    headcomments.push(tokens[i]);
    i++;
  }
  tokens[i].leadingComments = headcomments;
  tokens[i].trailingComments = [];
  newTokens.push(tokens[i]);
  let lastToken = tokens[i];

  for (++i; i < tokens.length; i++) {
    const currTok = tokens[i];
    if (tokenMatcher(currTok.tokenType, t.Comment)) {
      const nextToken = findNextNonCommentToken(i + 1, tokens);
      if (nextToken == -1) {
        lastToken.trailingComments.push(currTok);
      } else if (
        currTok.startOffset - lastToken.endOffset <
        tokens[nextToken].startOffset - currTok.endOffset
      ) {
        lastToken.trailingComments.push(currTok);
      } else {
        if (!currTok.leadingComments) {
          tokens[nextToken].leadingComments = [];
        }
        tokens[nextToken].leadingComments.push(currTok);
      }
    } else {
      if (!currTok.leadingComments) {
        currTok.leadingComments = [];
      }
      if (!currTok.trailingComments) {
        currTok.trailingComments = [];
      }
      lastToken = currTok;
      newTokens.push(currTok);
    }
  }

  parser.input = newTokens;
  // Automatic CST created when parsing
  const cst = parser[entryPoint]();
  if (parser.errors.length > 0) {
    const error = parser.errors[0];
    throw Error(
      "Sad sad panda, parsing errors detected in line: " +
        error.token.startLine +
        ", column: " +
        error.token.startColumn +
        "!\n" +
        error.message +
        "!\n\t->" +
        error.context.ruleStack.join("\n\t->")
    );
  }

  return cst;
}

function findNextNonCommentToken(startOffset, tokens) {
  let nextToken = startOffset;
  if (nextToken >= tokens.length) {
    return -1;
  }
  while (
    tokenMatcher(tokens[nextToken].tokenType, t.Comment) &&
    nextToken < tokens.length
  ) {
    nextToken++;
  }
  return nextToken >= tokens.length ? -1 : nextToken;
}

module.exports = {
  parse,
  BaseJavaCstVisitor,
  BaseJavaCstVisitorWithDefaults
};
