/*
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
package io.trino.operator;

import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import io.trino.operator.scalar.CombineHashFunction;
import io.trino.spi.Page;
import io.trino.spi.block.Block;
import io.trino.spi.type.Type;
import io.trino.sql.planner.optimizations.HashGenerationOptimizer;
import io.trino.type.BlockTypeOperators;
import io.trino.type.BlockTypeOperators.BlockPositionHashCode;

import java.util.List;
import java.util.function.IntFunction;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

public class InterpretedHashGenerator
        implements HashGenerator
{
    private final List<Type> hashChannelTypes;
    private final int[] hashChannels;
    private final List<BlockPositionHashCode> hashCodeOperators;

    public InterpretedHashGenerator(List<Type> hashChannelTypes, List<Integer> hashChannels, BlockTypeOperators blockTypeOperators)
    {
        this(hashChannelTypes, Ints.toArray(requireNonNull(hashChannels, "hashChannels is null")), blockTypeOperators);
    }

    public InterpretedHashGenerator(List<Type> hashChannelTypes, int[] hashChannels, BlockTypeOperators blockTypeOperators)
    {
        this.hashChannels = requireNonNull(hashChannels, "hashChannels is null");
        this.hashChannelTypes = ImmutableList.copyOf(requireNonNull(hashChannelTypes, "hashChannelTypes is null"));
        checkArgument(hashChannelTypes.size() == hashChannels.length);
        this.hashCodeOperators = hashChannelTypes.stream()
                .map(blockTypeOperators::getHashCodeOperator)
                .collect(toImmutableList());
    }

    @Override
    public long hashPosition(int position, Page page)
    {
        return hashPosition(position, page::getBlock);
    }

    public long hashPosition(int position, IntFunction<Block> blockProvider)
    {
        long result = HashGenerationOptimizer.INITIAL_HASH_VALUE;
        for (int i = 0; i < hashChannels.length; i++) {
            Block block = blockProvider.apply(hashChannels[i]);
            result = CombineHashFunction.getHash(result, hashCodeOperators.get(i).hashCodeNullSafe(block, position));
        }
        return result;
    }

    @Override
    public String toString()
    {
        return toStringHelper(this)
                .add("hashChannelTypes", hashChannelTypes)
                .add("hashChannels", hashChannels)
                .toString();
    }
}
