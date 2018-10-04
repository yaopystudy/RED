/*
 * Copyright 2018 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.rf.ide.core.testdata.model.table;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.rf.ide.core.testdata.model.AModelElement;
import org.rf.ide.core.testdata.model.ExecutableSetting;
import org.rf.ide.core.testdata.model.FilePosition;
import org.rf.ide.core.testdata.model.FileRegion;
import org.rf.ide.core.testdata.model.ICommentHolder;
import org.rf.ide.core.testdata.model.IDocumentationHolder;
import org.rf.ide.core.testdata.model.ModelType;
import org.rf.ide.core.testdata.text.read.IRobotTokenType;
import org.rf.ide.core.testdata.text.read.recognizer.RobotToken;
import org.rf.ide.core.testdata.text.read.recognizer.RobotTokenType;

import com.google.common.base.Preconditions;

public class LocalSetting<T> extends AModelElement<T> implements ICommentHolder, Serializable {

    private static final long serialVersionUID = 5810286313565051692L;

    public static final Set<ModelType> TEST_SETTING_TYPES = EnumSet.of(ModelType.TEST_CASE_DOCUMENTATION,
            ModelType.TEST_CASE_TAGS, ModelType.TEST_CASE_SETUP, ModelType.TEST_CASE_TEARDOWN,
            ModelType.TEST_CASE_TIMEOUT, ModelType.TEST_CASE_TEMPLATE, ModelType.TEST_CASE_SETTING_UNKNOWN);

    public static final Set<ModelType> TASK_SETTING_TYPES = EnumSet.of(ModelType.TASK_DOCUMENTATION,
            ModelType.TASK_TAGS, ModelType.TASK_SETUP, ModelType.TASK_TEARDOWN, ModelType.TASK_TIMEOUT,
            ModelType.TASK_TEMPLATE, ModelType.TASK_SETTING_UNKNOWN);

    public static final Set<ModelType> KEYWORD_SETTING_TYPES = EnumSet.of(ModelType.USER_KEYWORD_DOCUMENTATION,
            ModelType.USER_KEYWORD_TAGS, ModelType.USER_KEYWORD_TEARDOWN, ModelType.USER_KEYWORD_TIMEOUT,
            ModelType.USER_KEYWORD_ARGUMENTS, ModelType.USER_KEYWORD_RETURN, ModelType.USER_KEYWORD_SETTING_UNKNOWN);

    private ModelType modelType;

    private final List<RobotToken> tokens = new ArrayList<>(0);

    private final List<RobotToken> comments = new ArrayList<>(0);

    public LocalSetting(final ModelType modelType, final RobotToken declaration) {
        this.modelType = modelType;
        this.tokens.add(fixForTheType(Preconditions.checkNotNull(declaration),
                LocalSettingTokenTypes.getTokenType(modelType, 0)));
    }

    @Override
    public ModelType getModelType() {
        return modelType;
    }

    public void changeModelType(final ModelType type) {
        // firstly we remove types of tokens tied to old model type
        removeTypes(0);

        // then we change the type and properly add token types tied to new model type
        this.modelType = type;
        fixTypes();
    }

    private void removeTypes(final int startingIndex) {
        final List<RobotTokenType> tokenTypes = LocalSettingTokenTypes.getPossibleTokenTypes(modelType);
        for (int i = startingIndex; i < tokens.size(); i++) {
            final RobotToken token = tokens.get(i);
            for (final RobotTokenType type : tokenTypes) {
                if (token.getTypes().contains(type)) {
                    token.getTypes().removeIf(t -> t == type);
                }
            }
        }
    }

    @Override
    public boolean isPresent() {
        return !tokens.isEmpty();
    }

    @Override
    public RobotToken getDeclaration() {
        return isPresent() ? tokens.get(0) : null;
    }

    @Override
    public FilePosition getBeginPosition() {
        return getDeclaration().getFilePosition();
    }

    public List<RobotToken> getTokens() {
        return new ArrayList<>(tokens);
    }

    public List<RobotToken> getTokensWithoutDeclaration() {
        return new ArrayList<>(tokens.subList(1, tokens.size()));
    }

    public RobotToken getToken(final IRobotTokenType type) {
        return tokensOf(type).findFirst().orElse(null);
    }

    public Stream<RobotToken> tokensOf(final IRobotTokenType type) {
        return Stream.concat(tokens.stream(), comments.stream()).filter(token -> token.getTypes().contains(type));
    }

    @Override
    public List<RobotToken> getElementTokens() {
        final List<RobotToken> tokens = new ArrayList<>();
        if (isPresent()) {
            tokens.addAll(this.tokens);
            tokens.addAll(this.comments);
        }
        return tokens;
    }

    @Override
    public boolean removeElementToken(final int index) {
        return super.removeElementFromList(tokens, index);
    }

    @Override
    public void insertValueAt(final String value, final int position) {
        final RobotToken tokenToInsert = RobotToken.create(value);
        if (1 <= position && position <= tokens.size()) {
            removeTypes(position);
            tokens.add(position, tokenToInsert);
            fixTypes();

        } else if (position > tokens.size()) {
            final int commentsIndex = position - tokens.size();
            fixForTheType(tokenToInsert,
                    commentsIndex == 0 ? RobotTokenType.START_HASH_COMMENT : RobotTokenType.COMMENT_CONTINUE);
            if (commentsIndex == 0) {
                comments.get(0).getTypes().remove(RobotTokenType.START_HASH_COMMENT);
                comments.get(0).getTypes().add(RobotTokenType.COMMENT_CONTINUE);
            }
            comments.add(commentsIndex, tokenToInsert);
        }
    }

    public void addToken(final RobotToken token) {
        this.tokens.add(fixType(token, tokens.size()));
    }

    private void fixTypes() {
        for (int i = 0; i < tokens.size(); i++) {
            fixType(tokens.get(i), i);
        }
    }

    private RobotToken fixType(final RobotToken token, final int index) {
        final List<IRobotTokenType> types = token.getTypes();
        final IRobotTokenType type = LocalSettingTokenTypes.getTokenType(modelType, index);
        if (type != null) {
            types.remove(RobotTokenType.UNKNOWN);
        }
        if (!types.contains(type)) {
            types.add(0, type);
        }
        if (types.isEmpty()) {
            types.add(RobotTokenType.UNKNOWN);
        }
        return token;
    }

    public void addToken(final String tokenText) {
        final int index = tokens.size();
        tokens.add(RobotToken.create(tokenText, LocalSettingTokenTypes.getTokenType(modelType, index)));
    }

    public void setToken(final String tokenText, final int index) {
        if (tokenText == null && 0 <= index && index < tokens.size()) {
            tokens.remove(index);
        } else if (tokenText != null) {
            for (int i = tokens.size(); i <= index; i++) {
                tokens.add(RobotToken.create("", LocalSettingTokenTypes.getTokenType(modelType, i)));
            }
            tokens.set(index, RobotToken.create(tokenText, LocalSettingTokenTypes.getTokenType(modelType, index)));
        }
    }

    public void setTokens(final List<String> tokenTexts) {
        while (tokens.size() > 1) {
            tokens.remove(tokens.size() - 1);
        }
        for (int i = 0; i < tokenTexts.size(); i++) {
            tokens.add(RobotToken.create(tokenTexts.get(i), LocalSettingTokenTypes.getTokenType(modelType, i + 1)));
        }
    }

    @Override
    public List<RobotToken> getComment() {
        return Collections.unmodifiableList(comments);
    }

    public void addCommentPart(final String commentToken) {
        addCommentPart(RobotToken.create(commentToken));
    }

    @Override
    public void addCommentPart(final RobotToken rt) {
        fixComment(getComment(), rt);
        comments.add(rt);
    }

    @Override
    public void setComment(final String comment) {
        setComment(RobotToken.create(comment));
    }

    @Override
    public void setComment(final RobotToken comment) {
        comments.clear();
        addCommentPart(comment);
    }

    @Override
    public void removeCommentPart(final int index) {
        comments.remove(index);
    }

    @Override
    public void clearComment() {
        comments.clear();
    }

    @Override
    public String toString() {
        return modelType.name();
    }

    public <C> C adaptTo(final Class<C> clazz) {
        if (clazz == IDocumentationHolder.class
                && EnumSet
                        .of(ModelType.TEST_CASE_DOCUMENTATION, ModelType.TASK_DOCUMENTATION,
                                ModelType.USER_KEYWORD_DOCUMENTATION)
                        .contains(modelType)) {
            return clazz.cast(new DocumentationHolderAdapter());

        } else if (clazz == ExecutableSetting.class
                && EnumSet.of(ModelType.TEST_CASE_SETUP, ModelType.TASK_SETUP).contains(modelType)) {
            return clazz.cast(new ExecutableAdapter(true));

        } else if (clazz == ExecutableSetting.class
                && EnumSet.of(ModelType.TEST_CASE_TEARDOWN, ModelType.TASK_TEARDOWN, ModelType.USER_KEYWORD_TEARDOWN)
                        .contains(modelType)) {
            return clazz.cast(new ExecutableAdapter(false));
        }
        return null;
    }

    private class ExecutableAdapter implements ExecutableSetting {

        private final boolean isSetup;

        public ExecutableAdapter(final boolean isSetup) {
            this.isSetup = isSetup;
        }

        @Override
        public RobotToken getDeclaration() {
            return LocalSetting.this.getDeclaration();
        }

        @Override
        public RobotToken getKeywordName() {
            final List<RobotToken> tokens = getTokens();
            return tokens.size() > 1 ? tokens.get(1) : null;
        }

        @Override
        public List<RobotToken> getArguments() {
            final List<RobotToken> tokens = getTokens();
            final List<RobotToken> arguments = tokens.size() > 2 ? tokens.subList(2, tokens.size()) : new ArrayList<>();
            return Collections.unmodifiableList(arguments);
        }

        @SuppressWarnings("unchecked")
        @Override
        public RobotExecutableRow<T> asExecutableRow() {
            final RobotExecutableRow<T> execRow = new RobotExecutableRow<>();
            execRow.setParent(getParent());

            execRow.setAction(getKeywordName().copy());
            for (final RobotToken arg : getArguments()) {
                execRow.addArgument(arg.copy());
            }
            for (final RobotToken c : getComment()) {
                execRow.addCommentPart(c.copy());
            }
            return execRow;
        }

        @Override
        public boolean isSetup() {
            return isSetup;
        }

        @Override
        public boolean isTeardown() {
            return !isSetup;
        }
    }

    private class DocumentationHolderAdapter implements IDocumentationHolder {

        @Override
        public List<FileRegion> getContinuousRegions() {
            return new FileRegion.FileRegionSplitter().splitContinuousRegions(getElementTokens());
        }

        @Override
        public IDocumentationHolder getCached() {
            return this;
        }

        @Override
        public FilePosition getBeginPosition() {
            return getDeclaration().getFilePosition();
        }

        @Override
        public List<RobotToken> getDocumentationText() {
            return Collections.unmodifiableList(tokens.subList(1, tokens.size()));
        }

        @Override
        public void addDocumentationText(final RobotToken token) {
            token.getTypes().clear();
            token.getTypes().add(LocalSettingTokenTypes.getTokenType(modelType, 1));
            tokens.add(token);
        }

        @Override
        public void clearDocumentation() {
            while (tokens.size() > 1) {
                tokens.remove(tokens.size() - 1);
            }
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj != null && obj.getClass() == DocumentationHolderAdapter.class) {
                final LocalSetting<?>.DocumentationHolderAdapter that = (LocalSetting<?>.DocumentationHolderAdapter) obj;

                return this.getOuterSettingObject().equals(that.getOuterSettingObject());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return getOuterSettingObject().hashCode();
        }

        private LocalSetting<?> getOuterSettingObject() {
            return LocalSetting.this;
        }
    }
}
