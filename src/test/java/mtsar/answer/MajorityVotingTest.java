/*
 * Copyright 2015 Dmitry Ustalov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mtsar.answer;

import com.google.common.collect.Lists;
import mtsar.api.Answer;
import mtsar.api.AnswerAggregation;
import mtsar.api.Process;
import mtsar.api.Task;
import mtsar.api.sql.AnswerDAO;
import mtsar.processors.AnswerAggregator;
import mtsar.processors.answer.MajorityVoting;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;

import static mtsar.TestHelper.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class MajorityVotingTest {
    private static final AnswerDAO answerDAO = mock(AnswerDAO.class);
    private static final Process process = mock(Process.class);
    private static final Task task = fixture("task1.json", Task.class);
    private static final AnswerAggregator aggregator = new MajorityVoting(() -> process, answerDAO);

    @Before
    public void setup() {
        when(process.getId()).thenReturn("1");
    }

    @Test
    public void testBasicCase() {
        reset(answerDAO);
        when(answerDAO.listForTask(eq(1), anyString())).thenReturn(Lists.newArrayList(
                new Answer.Builder().addAnswers("1").buildPartial(),
                new Answer.Builder().addAnswers("1").buildPartial(),
                new Answer.Builder().addAnswers("2").buildPartial(),
                new Answer.Builder().addAnswers("3").buildPartial()
        ));
        assertThatThrownBy(() -> {
            final Optional<AnswerAggregation> winner = aggregator.aggregate(task);
            assertThat(winner.isPresent()).isTrue();
            assertThat(winner.get().getAnswers()).hasSize(1);
            assertThat(winner.get().getAnswers().get(0)).isEqualTo("1");
        }).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Not Implemented Yet");
    }

    @Test
    public void testAmbiguousCase() {
        reset(answerDAO);
        when(answerDAO.listForTask(eq(1), anyString())).thenReturn(Lists.newArrayList(
                new Answer.Builder().addAnswers("2").buildPartial(),
                new Answer.Builder().addAnswers("1").buildPartial()
        ));
        assertThatThrownBy(() -> {
            final Optional<AnswerAggregation> winner = aggregator.aggregate(task);
            assertThat(winner.isPresent()).isTrue();
            assertThat(winner.get().getAnswers()).hasSize(1);
            assertThat(winner.get().getAnswers().get(0)).isIn("1", "2");
        }).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Not Implemented Yet");
    }

    @Test
    public void testEmptyCase() {
        reset(answerDAO);
        when(answerDAO.listForTask(eq(1), anyString())).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> {
            final Optional<AnswerAggregation> winner = aggregator.aggregate(task);
            assertThat(winner.isPresent()).isFalse();
        }).isInstanceOf(UnsupportedOperationException.class).hasMessageContaining("Not Implemented Yet");
    }
}
