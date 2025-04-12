/**
 * This package holds classes that help hold necessary system state.
 * <p>
 *  There are just two classes in this package, but they are commonly used
 *  throughout the Minum application.
 * </p>
 * <p>
 *     One is {@link com.renomad.minum.state.Constants}, which contains constant
 *     values that are used in various places.  For example, the port that is
 *     opened for secure endpoints.
 * </p>
 * <p>
 *     Each value has a corresponding entry in the minum.config file, allowing
 *     users to adjust parameters without needing to recompile.
 * </p>
 * <p>
 *     The second class in this package is {@link com.renomad.minum.state.Context}, which
 *     holds a reference to Constants and several other widely-needed items, so that
 *     code in later parts of the call tree have access.
 * </p>
 */
package com.renomad.minum.state;